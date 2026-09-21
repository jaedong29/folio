# 해커톤 제출용 HTTPS

예정 주소: `https://folio.15.164.225.198.sslip.io`.
이 파일에 주소가 있다는 사실은 인증서 발급이나 서버 반영이 완료됐다는 뜻이 아니다.

sslip.io는 주소에 포함된 IP를 반환하는 무료 DNS다. 도메인 구매·가입은 필요 없지만 해당 서비스와 EC2 공인 IP에 의존한다. 인스턴스 중지·시작 등으로 IP가 바뀌면 제출 링크도 바뀔 수 있으므로 심사 기간 중 변경하지 않는다. 고정 IP를 별도로 할당할 경우에는 AWS 요금을 확인한다.

## 서버 적용

1. 현재 서버의 저장소·Compose 프로젝트·DB 볼륨을 그대로 사용한다. 새 디렉터리에 별도 스택을 만들거나 `down -v`를 실행하지 않는다.
2. `./deploy/aws/backup.sh`로 기존 DB를 백업한다.
3. EC2 보안 그룹에서 TCP 80과 443을 공개한다. SSH 22는 관리자 IP만 허용하고, 3306은 공개하지 않는다.
4. Docker Compose 2.24.4 이상인지 확인한다. 설정의 `!override`는 기존 8080 공개 바인딩을 제거하는 데 필요하다.
5. 새 배포 파일을 반영하고 `deploy/aws/.env`에 다음 두 줄을 추가한다. 기존 비밀키와 DB 설정은 유지한다.

```dotenv
COMPOSE_FILE=docker-compose.yml:docker-compose.https.yml
FOLIO_DOMAIN=folio.15.164.225.198.sslip.io
```

6. 설정 검증과 Caddy 검증을 먼저 실행한다. 인증서 발급은 실제 서비스가 시작될 때 수행된다.

```bash
cd deploy/aws
docker compose config --quiet
docker compose run --rm --no-deps caddy caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile
docker compose up -d --no-deps caddy
curl --fail https://folio.15.164.225.198.sslip.io/actuator/health
```

최초 인증서 발급에는 시간이 걸릴 수 있다. 위 HTTPS 검증이 성공하기 전에는 다음 단계로 진행하지 않는다. 이 단계는 기존 앱을 재생성하지 않으므로 기존 8080 접속을 유지한다. 인증서 오류가 나면 Caddy 로그와 보안 그룹 80/443을 확인한다.

7. HTTPS 검증 후 앱의 비공개 바인딩과 운영 설정을 적용한다.

```bash
cd ../..
./deploy/aws/bootstrap.sh
```

8. HTTPS에서 다시 로그인·자산 조회를 확인한 후 보안 그룹의 외부 8080 허용 규칙을 제거한다. 오버레이 자체도 앱 포트를 127.0.0.1에만 바인딩한다.

인증서와 갱신 상태는 `caddy-data` 볼륨에 유지된다. Caddy는 인증서를 자동 갱신하고 HTTP를 HTTPS로 보낸다. 원래 숫자 IP의 8080 주소는 제출 링크로 사용하지 않는다. HTTPS 전환 시 브라우저 저장소의 출처가 바뀌므로 한 번 다시 로그인해야 한다.

## 적용되는 보호

- 외부에서는 Caddy의 80/443으로 접속하고 앱 8080과 MySQL은 직접 노출하지 않는다.
- Caddy가 전달 IP·프로토콜 헤더를 덮어쓰고 Spring의 native forwarding을 활성화한다. 프록시 도입 때문에 모든 방문자가 같은 IP로 묶여 로그인 제한에 걸리는 문제를 피한다. 앱을 다시 공인 인터페이스에 공개한 채 forwarding만 켜두지 않는다.
- 운영 지표·Swagger·H2 콘솔·AI 평가 실행 주소는 외부 프록시에서 차단한다. 앱에서도 metrics 노출과 Swagger를 끈다. 일반 사용자용 AI 기능은 유지한다.
- 기존 CSP·키 암호화 정책을 유지한다. HTTPS 적용만으로 실계좌 연동 검증이나 제3자 API 서비스 이용 조건 검토가 완료되는 것은 아니다.
- `.dockerignore`가 로컬 계좌 DB·환경변수 파일·백업·개인키를 이미지 빌드 컨텍스트에서 제외한다.

## 제출 직전 확인

```bash
curl -I http://folio.15.164.225.198.sslip.io/
curl --fail https://folio.15.164.225.198.sslip.io/actuator/health
```

첫 요청은 HTTPS로 리다이렉트되어야 하고, 두 번째 요청은 인증서 검증을 통과하며 `UP`이어야 한다. 인증서 검증을 생략하는 `-k` 옵션을 쓰지 않는다. 브라우저에서 데모 계정 로그인·자산 목록·시세 조회를 확인한다. 공개 데모 계정에는 실제 거래소 키나 개인 자산을 넣지 않는다. 현재 공개 데모 계정은 다른 일반 계정과 동일하게 비밀번호 변경·탈퇴가 가능하므로 심사 중 계정 유지 여부도 확인한다.

## 문제 발생 시

`cd deploy/aws && docker compose logs --tail=60 caddy`로 DNS·포트·인증서 오류를 확인한다. 발급 요청을 무작정 반복하지 않는다. 원래 HTTP 구성으로 복구하려면 `.env`의 `COMPOSE_FILE`을 제거하고 `docker compose up -d --build app`을 실행하면 되지만, 앱 8080이 다시 공개될 수 있으므로 보안 그룹은 관리자 IP만 허용한 상태에서 수행한다. 인증서 볼륨과 DB 볼륨을 지울 필요는 없다.

공식 참고: [sslip.io](https://sslip.io/), [Caddy HTTPS](https://caddyserver.com/docs/automatic-https), [Compose override](https://docs.docker.com/reference/compose-file/merge/).

## 로컬 준비 검증 (2026-09-21)

- 무료 호스트의 IPv4 DNS 응답이 `15.164.225.198`인 것을 확인했다.
- Caddy 2.11.4 공식 배포본의 체크섬을 확인하고 `validate`를 통과했다.
- Compose 최종 설정에서 앱 공개 포트가 추가되는 대신 127.0.0.1 바인딩 하나로 교체되는 것을 확인했다.
- 기존 화면 회귀 테스트 8개와 변경 파일 공백 검사를 통과했다.
- 기존 HTTP 배포 주소의 health·보안 헤더·제공된 데모 계정 로그인과 조회를 확인했다. 계좌 연결은 꺼져 있었다.
- 사용자의 요청에 따라 서버 접속과 실행은 사용자가 직접 수행한다. 서버 설정 변경·인증서 발급·외부 HTTPS 검증은 아직 하지 않았다.
