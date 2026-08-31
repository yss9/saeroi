# Docker CLI + 파트별 빌드 툴체인 + 사전 설치 플러그인 + JCasC가 포함된 Jenkins 이미지.
# 기동 시 casc.yaml이 자동 적용되어 웹에서의 초기 세팅(마법사/계정/Job 등록)이 필요 없다.
#
# 베이스 태그는 고정한다. `lts` 롤링 태그를 쓰면 어느 날 재빌드에서 코어 메이저가 올라가고
# jenkins-plugin-cli가 다른 플러그인 조합을 끌어와 JCasC가 조용히 깨진다.
# 갱신 시: EC2에서 현재 버전 확인 후 태그 존재 여부를 docker manifest inspect로 검증할 것.
FROM jenkins/jenkins:2.541.3-lts-jdk21

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

# 파트 파이프라인이 컨트롤러에서 직접 빌드하므로 툴체인이 필요하다.
# - Node 22: frontend(Vite 8은 Node 20.19+/22.12+ 요구), blockchain(hardhat)
# - Python3 + venv: ai
# backend(Maven)는 베이스의 JDK21을 그대로 쓴다 (pom.xml java.version=21).
RUN apt-get update \
 && curl -fsSL https://deb.nodesource.com/setup_22.x | bash - \
 && apt-get install -y --no-install-recommends nodejs python3 python3-venv python3-pip \
 && rm -rf /var/lib/apt/lists/* \
 && node --version && npm --version && python3 --version

# 플러그인 사전 설치 + 초기 설치 마법사 생략 + JCasC 설정 경로
COPY plugins.txt /usr/share/jenkins/ref/plugins.txt
RUN jenkins-plugin-cli --plugin-file /usr/share/jenkins/ref/plugins.txt
COPY casc.yaml /var/jenkins_casc/casc.yaml
# NOTIFY_COMMIT_ACCESS_CONTROL: GitLab webhook이 /git/notifyCommit으로 인덱싱을 트리거하려면
# 익명 접근이 필요하다 (casc.yaml의 allowAnonymousRead: false 때문에 막힘).
# 이 엔드포인트는 빌드 파라미터를 받지 않아 위험도가 낮지만, 8081은 UFW/보안그룹으로 좁히는 게 좋다.
ENV JAVA_OPTS="-Djenkins.install.runSetupWizard=false -Dhudson.plugins.git.GitStatus.NOTIFY_COMMIT_ACCESS_CONTROL=disabled"
ENV CASC_JENKINS_CONFIG=/var/jenkins_casc/casc.yaml

# docker.sock 접근 권한 문제를 피하기 위해 root로 실행한다.
# (호스트 docker 그룹 GID와 컨테이너 GID가 달라 jenkins 유저로는 소켓 접근이 번거로움.
#  팀 내부 CI 용도로 한정하고, 8081 포트는 UFW/보안그룹에서 팀 IP로 제한할 것)
USER root
