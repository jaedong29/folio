package com.assetdashboard.domain.transaction.service;

import com.assetdashboard.domain.asset.entity.Asset;
import com.assetdashboard.domain.asset.exception.InsufficientAssetQuantityException;
import com.assetdashboard.domain.asset.service.AssetService;
import com.assetdashboard.domain.transaction.dto.CashFlowRequest;
import com.assetdashboard.domain.transaction.dto.TradeRequest;
import com.assetdashboard.domain.transaction.dto.TransactionHistoryResponse;
import com.assetdashboard.domain.transaction.dto.TransactionResponse;
import com.assetdashboard.domain.transaction.entity.Transaction;
import com.assetdashboard.domain.transaction.repository.TransactionRepository;
import com.assetdashboard.global.exception.BusinessException;
import com.assetdashboard.global.exception.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 거래를 기록하고 그 결과를 Asset 에 반영한다.
 *
 * <p>이 클래스는 <b>수량이나 평단가를 직접 계산하지 않는다.</b> 계산은 전부 {@link Asset} 의 도메인 메서드가
 * 수행하고, 여기서는 "소유권 확인 → 이벤트 생성 → Asset 에 적용 → 이벤트 저장" 순서를 조율하기만 한다.
 *
 * <p>Asset 은 트랜잭션 안에서 조회된 영속 상태이므로 JPA 더티 체킹으로 자동 UPDATE 되며, 이때 {@code @Version}
 * 이 함께 검증되어 동시에 들어온 다른 거래가 먼저 커밋했다면 {@code OptimisticLockException} 으로 롤백된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TransactionService {

  private final TransactionRepository transactionRepository;
  private final AssetService assetService;

  /**
   * 매수를 기록한다.
   *
   * @param userId 인증된 사용자 id
   * @param assetId 대상 자산 id
   * @param request 매수 요청
   * @return 생성된 거래와 반영 후 자산 상태
   * @throws BusinessException 자산이 없거나 타인 소유이면 {@code ASSET_NOT_FOUND}, 투자 자산이 아니면
   *     {@code INVALID_INPUT}
   */
  @Transactional
  public TransactionResponse buy(Long userId, Long assetId, TradeRequest request) {
    Asset asset = assetService.getOwnedAsset(userId, assetId);
    Transaction tx =
        Transaction.createBuy(
            assetId,
            request.quantity(),
            request.price(),
            request.exchangeRate(),
            request.memo(),
            request.tradedAt());
    return apply(asset, tx);
  }

  /**
   * 매도를 기록한다.
   *
   * @param userId 인증된 사용자 id
   * @param assetId 대상 자산 id
   * @param request 매도 요청
   * @return 생성된 거래와 반영 후 자산 상태
   * @throws BusinessException 보유 수량을 초과하면 {@code INSUFFICIENT_ASSET_QUANTITY}
   */
  @Transactional
  public TransactionResponse sell(Long userId, Long assetId, TradeRequest request) {
    Asset asset = assetService.getOwnedAsset(userId, assetId);
    Transaction tx =
        Transaction.createSell(
            assetId,
            request.quantity(),
            request.price(),
            request.exchangeRate(),
            request.memo(),
            request.tradedAt());
    return apply(asset, tx);
  }

  /**
   * 입금을 기록한다.
   *
   * @param userId 인증된 사용자 id
   * @param assetId 대상 자산 id
   * @param request 입금 요청
   * @return 생성된 거래와 반영 후 자산 상태
   * @throws BusinessException 현금성 자산이 아니면 {@code INVALID_INPUT}
   */
  @Transactional
  public TransactionResponse deposit(Long userId, Long assetId, CashFlowRequest request) {
    Asset asset = assetService.getOwnedAsset(userId, assetId);
    Transaction tx =
        Transaction.createDeposit(assetId, request.quantity(), request.memo(), request.tradedAt());
    return apply(asset, tx);
  }

  /**
   * 출금을 기록한다.
   *
   * @param userId 인증된 사용자 id
   * @param assetId 대상 자산 id
   * @param request 출금 요청
   * @return 생성된 거래와 반영 후 자산 상태
   * @throws BusinessException 잔액이 부족하면 {@code INSUFFICIENT_ASSET_QUANTITY}
   */
  @Transactional
  public TransactionResponse withdraw(Long userId, Long assetId, CashFlowRequest request) {
    Asset asset = assetService.getOwnedAsset(userId, assetId);
    Transaction tx =
        Transaction.createWithdraw(assetId, request.quantity(), request.memo(), request.tradedAt());
    return apply(asset, tx);
  }

  /**
   * 잘못 입력한 거래를 삭제하고 자산 상태를 남은 이력으로부터 다시 계산한다.
   *
   * <p><b>PRD 1장은 Transaction 을 append-only 이벤트 로그로 정의했고, 이 기능은 거기서 벗어난다.</b>
   * append-only 는 시스템이 생성하는 이벤트에는 맞지만, <b>사람이 손으로 입력하는 이벤트</b>에는 오타 정정
   * 경로가 반드시 필요하다. 수량을 0.5 대신 5로 잘못 넣었을 때 그것을 되돌릴 방법이 없으면, 사용자는 자산을
   * 통째로 지우거나(다른 거래까지 잃는다) 반대 매매를 입력하는(실현손익이 오염된다) 수밖에 없다.
   *
   * <p><b>왜 "빼기"가 아니라 "다시 접기(replay)"인가</b>: 매도의 실현손익은 <em>그 시점의 평단가</em>에
   * 의존한다. 중간 거래 하나가 사라지면 그 이후 모든 계산의 전제가 바뀌므로, 삭제된 거래의 영향만 역산해서
   * 빼는 것은 성립하지 않는다. 남은 이력으로 처음부터 다시 계산하는 것이 유일하게 정확한 방법이다.
   *
   * <p>재생 순서는 <b>{@code tradedAt} 오름차순</b>이다. 사용자가 지정한 실제 거래 시점 순서로 계산해야
   * 현실과 일치하며, 과거 날짜 거래를 나중에 입력해도 숫자가 올바르게 정렬된다.
   *
   * @param userId 인증된 사용자 id
   * @param assetId 대상 자산 id
   * @param transactionId 삭제할 거래 id
   * @return 재계산된 자산 상태
   * @throws BusinessException 자산이 없거나 타인 소유이면 {@code ASSET_NOT_FOUND}, 해당 자산의 거래가
   *     아니면 {@code TRANSACTION_NOT_FOUND}, 삭제 시 이후 거래의 수량이 음수가 되면
   *     {@code TRANSACTION_DELETE_BREAKS_HISTORY}
   */
  @Transactional
  public TransactionResponse.AssetSnapshot delete(Long userId, Long assetId, Long transactionId) {
    Asset asset = assetService.getOwnedAsset(userId, assetId);
    Transaction target =
        transactionRepository
            .findByIdAndAssetId(transactionId, assetId)
            .orElseThrow(() -> new BusinessException(ErrorCode.TRANSACTION_NOT_FOUND));

    transactionRepository.delete(target);
    // 삭제를 DB 에 먼저 반영해야 이어지는 조회가 "남은 이력"을 정확히 돌려준다.
    transactionRepository.flush();

    List<Transaction> remaining =
        transactionRepository.findAllByAssetIdOrderByTradedAtAscIdAsc(assetId);

    try {
      asset.replay(remaining);
    } catch (InsufficientAssetQuantityException e) {
      // 매수 10 → 매도 5 상태에서 매수를 지우려는 경우다. 데이터를 음수로 망가뜨리는 대신 삭제를 거부한다.
      // 예외를 던지면 위의 delete 까지 함께 롤백된다.
      throw new BusinessException(ErrorCode.TRANSACTION_DELETE_BREAKS_HISTORY);
    }

    log.info(
        "[Transaction] 거래 삭제 후 재계산 assetId={} txId={} 남은거래={}건 quantity={} avgPrice={} realizedPnl={}",
        assetId,
        transactionId,
        remaining.size(),
        asset.getQuantity(),
        asset.getAvgPrice(),
        asset.getRealizedPnl());

    return TransactionResponse.AssetSnapshot.from(asset);
  }

  /**
   * 특정 자산의 거래 내역을 조회한다.
   *
   * @param userId 인증된 사용자 id
   * @param assetId 대상 자산 id
   * @return 최신순 거래 내역
   * @throws BusinessException 자산이 없거나 타인 소유이면 {@code ASSET_NOT_FOUND}
   */
  public List<TransactionHistoryResponse> getHistory(Long userId, Long assetId) {
    // Transaction 을 직접 조회하기 전에 반드시 Asset 의 소유권을 먼저 확인한다.
    // transactions 테이블에는 user_id 가 없으므로, 이 한 줄이 인가의 유일한 관문이다.
    assetService.getOwnedAsset(userId, assetId);
    return transactionRepository.findAllByAssetIdOrderByTradedAtDescIdDesc(assetId).stream()
        .map(TransactionHistoryResponse::from)
        .toList();
  }

  /**
   * 거래를 자산에 반영하고 이벤트를 저장한다.
   *
   * <p>거래가 이력의 <b>맨 뒤에 붙는지, 중간에 끼는지</b>에 따라 처리가 갈린다.
   *
   * <ul>
   *   <li><b>맨 뒤(일반적인 경우)</b>: 증분 계산. 먼저 {@code applyTransaction} 으로 도메인 규칙(수량 부족,
   *       타입 불일치)을 검증·반영한 뒤 이벤트를 저장한다. 반대 순서로 하면 실패한 거래가 로그에 남는다.
   *   <li><b>중간(과거 시점 거래를 뒤늦게 입력)</b>: 증분 계산이 성립하지 않는다. 매도의 실현손익은 <em>그
   *       시점의 평단가</em>에 의존하는데, 앞쪽에 매수가 하나 끼어들면 그 이후 모든 계산의 전제가 바뀌기
   *       때문이다. 저장한 뒤 전체 이력을 {@code tradedAt} 순으로 다시 접어 계산과 검증을 함께 수행한다.
   * </ul>
   *
   * <p>이 분기가 없으면 같은 이력이라도 <b>입력한 순서에 따라 평단가가 달라진다.</b> 실측 예시:
   *
   * <pre>
   *   08-01 매수 10@1,500 → 08-03 매도 5@2,000 → (뒤늦게) 08-02 매수 5@1,600
   *     증분만 적용   → avgPrice 1,550.00       realizedPnl 2,500.00
   *     시간순 재생   → avgPrice 1,533.33333333 realizedPnl 2,333.33333335  ← 이쪽이 맞다
   * </pre>
   *
   * @param asset 대상 자산 (영속 상태)
   * @param tx 적용할 거래
   * @return 거래 응답
   */
  private TransactionResponse apply(Asset asset, Transaction tx) {
    boolean insertedInMiddle =
        transactionRepository.existsByAssetIdAndTradedAtGreaterThan(
            asset.getId(), tx.getTradedAt());

    if (insertedInMiddle) {
      transactionRepository.save(tx);
      transactionRepository.flush();
      // 재생 중 수량이 음수가 되면 예외가 나고 이 트랜잭션 전체가 롤백된다 — 즉 저장도 취소된다.
      asset.replay(transactionRepository.findAllByAssetIdOrderByTradedAtAscIdAsc(asset.getId()));
      log.info("[Transaction] 과거 시점 거래 입력 — 이력 전체 재계산 assetId={} txId={}", asset.getId(), tx.getId());
    } else {
      asset.applyTransaction(tx);
      transactionRepository.save(tx);
    }
    // asset 은 영속 상태이므로 더티 체킹으로 UPDATE 된다. 명시적 save() 는 불필요하다.
    return TransactionResponse.from(tx, asset);
  }
}
