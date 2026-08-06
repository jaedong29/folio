package com.assetdashboard.domain.transaction.service;

import com.assetdashboard.domain.asset.entity.Asset;
import com.assetdashboard.domain.asset.service.AssetService;
import com.assetdashboard.domain.transaction.dto.CashFlowRequest;
import com.assetdashboard.domain.transaction.dto.TradeRequest;
import com.assetdashboard.domain.transaction.dto.TransactionHistoryResponse;
import com.assetdashboard.domain.transaction.dto.TransactionResponse;
import com.assetdashboard.domain.transaction.entity.Transaction;
import com.assetdashboard.domain.transaction.repository.TransactionRepository;
import com.assetdashboard.global.exception.BusinessException;
import java.util.List;
import lombok.RequiredArgsConstructor;
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
   * <p>순서가 중요하다. 먼저 {@code applyTransaction} 으로 도메인 규칙(수량 부족, 타입 불일치)을 검증·반영하고,
   * 그 다음에 이벤트를 저장한다. 반대로 하면 실패한 거래가 로그에 남는다.
   *
   * @param asset 대상 자산 (영속 상태)
   * @param tx 적용할 거래
   * @return 거래 응답
   */
  private TransactionResponse apply(Asset asset, Transaction tx) {
    asset.applyTransaction(tx);
    transactionRepository.save(tx);
    // asset 은 영속 상태이므로 더티 체킹으로 UPDATE 된다. 명시적 save() 는 불필요하다.
    return TransactionResponse.from(tx, asset);
  }
}
