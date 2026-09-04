# Folio AWS 1회 배포 가이드

이 문서는 SK 지원서에 적을 수 있는 실제 배포 경험을 만들기 위한 **EC2 단일 인스턴스 스테이징** 절차다. 공개 서비스 운영을 완료했다는 의미가 아니다. 사용자가 생기면 DB를 RDS로 분리하고 HTTPS, 백업·복구, 알림을 추가한다.

## 이번 배포의 완료 기준

- EC2에서 Docker Compose로 Spring Boot와 MySQL이 함께 실행된다.
- `/actuator/health`가 `UP`을 반환한다.
- 실제 URL에서 가입 → 로그인 → 자산 등록 → 대시보드 조회가 된다.
- 외부 시세 제공자를 끈 상태에서도 마지막 정상값과 `stale` 상태가 보인다.
- 애플리케이션 컨테이너를 재시작해도 MySQL volume의 계정·자산 데이터가 남는다.
- JWT 비밀키를 주입하지 않으면 운영 프로필이 기동되지 않는다.

## 1. EC2 준비

처음에는 가장 작은 범위의 Linux EC2를 사용한다. 보안 그룹은 다음처럼 시작한다.

| 포트 | 허용 범위 | 용도 |
|---|---|---|
| 22 | 내 IP만 | SSH |
| 8080 | 내 IP만 | 첫 smoke test |

`0.0.0.0/0`으로 22번 포트를 열지 않는다. 외부 사용자를 받을 단계가 되면 80/443과 HTTPS를 별도로 구성한다.

## 2. 서버에 Docker와 저장소 준비

EC2에 Docker와 Compose 플러그인을 설치한 뒤 저장소를 가져온다. 설치 방식은 선택한 Amazon Linux/Ubuntu 이미지의 공식 Docker 안내를 따른다.

```bash
git clone <저장소-주소> asset
cd asset
cp deploy/aws/.env.example deploy/aws/.env
chmod 600 deploy/aws/.env
```

`deploy/aws/.env`의 `APP_JWT_SECRET`, `DB_PASSWORD`, `MYSQL_ROOT_PASSWORD`를 실제 랜덤 값으로 바꾼다. 이 파일은 커밋하거나 채팅에 붙여넣지 않는다.

## 3. 배포 실행

```bash
./deploy/aws/bootstrap.sh
```

이 명령은 이미지를 빌드하고, MySQL이 healthy가 된 뒤 앱을 시작한다. `prod` 프로필에서는 Flyway가 빈 DB에 `V1__baseline.sql`을 적용하고 Hibernate가 스키마를 검증한다.

서버 밖에서 다음 주소를 연다.

```text
http://EC2_PUBLIC_IP:8080/
http://EC2_PUBLIC_IP:8080/actuator/health
```

## 4. 실제 smoke test

로컬 PC에서 API 주소만 바꾸어 기존 HTTP 검증을 실행한다.

```bash
B=http://EC2_PUBLIC_IP:8080 bash scripts/verify.sh
B=http://EC2_PUBLIC_IP:8080 bash scripts/verify-account.sh
```

외부 시세 장애는 EC2의 `.env`에서 `APP_PRICE_EXTERNAL_ENABLED=false`로 바꾼 뒤 앱을 재배포해 재현한다. AWS에서는 앱 로그와 dashboard 응답의 `priceStale`를 확인한다.

## 5. 재시작·보존 검증

```bash
cd deploy/aws
docker compose restart app
curl --fail http://localhost:8080/actuator/health
docker compose ps
```

계정을 다시 로그인해 데이터가 유지되는지 확인한다. `mysql-data` volume을 삭제하지 않는다. 데이터 삭제는 복구 기준을 정한 뒤 별도 승인하고 수행한다.

## 6. 백업·복구 검증

EC2 단일 인스턴스의 Docker volume은 컨테이너 재시작에는 강하지만, 인스턴스 손상·삭제까지 대비한 독립 백업은 아니다. 먼저 서버에서 백업을 만들고, 별도 임시 DB에 복구해 테이블별 건수가 같은지 확인한다.

```bash
cd ~/asset/deploy/aws
./backup.sh
./verify-backup.sh
```

백업 파일은 `deploy/aws/backups/`에 생성되며 `.gitignore`로 저장소에 들어가지 않는다. 실제 DB를 덮어써야 하는 복구는 반드시 명시적으로 승인한다.

```bash
CONFIRM_RESTORE=YES ./restore.sh backups/assetdashboard_YYYYMMDDTHHMMSSZ.sql.gz
```

`restore.sh`는 앱을 잠시 중지하고 현재 DB를 백업 시점으로 되돌린다. 복구 전 대상 파일과 checksum을 확인하고, 운영 중인 데이터에 실행하지 않는다.

## 7. 중단과 비용 관리

테스트가 끝나면 EC2를 중지하거나 종료한다. Elastic IP, EBS, 공개 IP, 데이터베이스 등 남아 있는 과금 가능 리소스를 AWS 콘솔에서 확인한다. 무료 사용 가능 여부와 기간은 계정 생성일·리전·서비스 조건에 따라 달라질 수 있으므로, 배포 전에 Billing 대시보드와 예산 알림을 확인한다.

## 문제 발생 시 확인 순서

```bash
cd deploy/aws
docker compose ps
docker compose logs --tail=100 mysql
docker compose logs --tail=100 app
```

- MySQL이 healthy가 아니면 비밀번호와 volume 상태를 확인한다.
- 앱이 `APP_JWT_SECRET` 오류로 종료되면 `.env`에 32바이트 이상 비밀키를 입력한다.
- Flyway가 이미 데이터가 있는 DB에서 baseline 오류를 내면 기존 DB를 지우지 말고 중지한다. 이 가이드는 새 스테이징 DB를 기준으로 한다.
- 외부에서 접속되지 않으면 앱 로그보다 먼저 EC2 보안 그룹과 인스턴스 포트 매핑을 확인한다.
