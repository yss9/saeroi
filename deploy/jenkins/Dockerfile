# Docker CLI + Compose plugin이 포함된 Jenkins 이미지.
# 호스트의 /var/run/docker.sock 을 공유받아 이미지 빌드와 compose 배포를 수행한다.
FROM jenkins/jenkins:lts-jdk17

USER root
RUN apt-get update \
 && apt-get install -y --no-install-recommends ca-certificates curl gnupg \
 && install -m 0755 -d /etc/apt/keyrings \
 && curl -fsSL https://download.docker.com/linux/debian/gpg -o /etc/apt/keyrings/docker.asc \
 && echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/debian $(. /etc/os-release && echo $VERSION_CODENAME) stable" \
      > /etc/apt/sources.list.d/docker.list \
 && apt-get update \
 && apt-get install -y --no-install-recommends docker-ce-cli docker-compose-plugin \
 && rm -rf /var/lib/apt/lists/*

# docker.sock 접근 권한 문제를 피하기 위해 root로 실행한다.
# (호스트 docker 그룹 GID와 컨테이너 GID가 달라 jenkins 유저로는 소켓 접근이 번거로움.
#  팀 내부 CI 용도로 한정하고, 8081 포트는 보안그룹에서 팀 IP로 제한할 것)
USER root
