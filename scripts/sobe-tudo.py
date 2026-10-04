#!/usr/bin/env python3
"""Sobe TODO o ambiente das aulas 7-9 com um unico comando, sem scripts .sh:

    python scripts/sobe-tudo.py            sobe tudo e mostra o resumo
    python scripts/sobe-tudo.py status     so mostra o resumo

Ordem: Docker Desktop -> compose (postgres, kafka, prometheus, grafana,
keycloak) -> containers avulsos (gitea, sonarqube, jfrog) -> cluster kind ->
port-forward do Tekton Dashboard -> lab da Aula 9 (:8080) -> painel (:8099).

Pre-requisito de primeira vez: o cluster kind e o Gitea precisam existir
(scripts/k8s-up.ps1 e scripts/local-git-server.sh uma unica vez). Depois disso,
este script religa tudo sozinho apos qualquer reboot.
"""

import json
import os
import subprocess
import sys
import time
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LAB9 = os.path.join(ROOT, "scripts", "aula9", "lab9.py")
PAINEL = os.path.join(ROOT, "scripts", "painel.py")
CHECKS = []


def run(cmd, timeout=180):
    try:
        p = subprocess.run(cmd, cwd=ROOT, capture_output=True, text=True, timeout=timeout)
        return p.returncode, (p.stdout or "") + (p.stderr or "")
    except (subprocess.TimeoutExpired, FileNotFoundError) as e:
        return 1, str(e)


def http_ok(url, timeout=4):
    try:
        with urllib.request.urlopen(url, timeout=timeout) as r:
            return 200 <= r.status < 400
    except Exception:
        return False


def wait_for(desc, fn, tries=60, delay=5):
    for i in range(tries):
        if fn():
            return True
        if i % 6 == 0:
            print("   ... aguardando %s (%ds)" % (desc, i * delay))
        time.sleep(delay)
    return False


def step(msg):
    print("\n== " + msg)


def ensure_docker():
    step("Docker")
    if run(["docker", "info", "--format", "ok"], 20)[0] == 0:
        print("   daemon ja esta rodando")
        return True
    exe = r"C:\Program Files\Docker\Docker\Docker Desktop.exe"
    if os.name == "nt" and os.path.exists(exe):
        print("   iniciando o Docker Desktop...")
        subprocess.Popen([exe], creationflags=0x00000008)
    ok = wait_for("docker daemon", lambda: run(["docker", "info", "--format", "ok"], 20)[0] == 0, 40, 5)
    print("   daemon %s" % ("pronto" if ok else "NAO subiu - abra o Docker Desktop na mao"))
    return ok


def container_running(name):
    rc, out = run(["docker", "ps", "--format", "{{.Names}}"], 20)
    return rc == 0 and name in out.split()


def container_exists(name):
    rc, out = run(["docker", "ps", "-a", "--format", "{{.Names}}"], 20)
    return rc == 0 and name in out.split()


def ensure_compose():
    step("docker compose: postgres, kafka, prometheus, grafana, keycloak")
    # containers antigos podem pertencer a outro nome de projeto compose
    # (a pasta ja se chamou aula7/aula8); docker start neles evita conflito de nome.
    legacy = [c for c in ("techpix-postgres", "techpix-kafka", "techpix-prometheus",
                          "techpix-grafana", "techpix-keycloak")
              if container_exists(c) and not container_running(c)]
    if legacy:
        run(["docker", "start"] + legacy, 120)
    rc, out = run(["docker", "compose", "--profile", "observability", "--profile", "auth",
                   "up", "-d", "postgres", "kafka", "prometheus", "grafana", "keycloak"], 300)
    if rc != 0 and "already in use" not in out:
        print("   AVISO compose: " + out.strip().splitlines()[-1][:160])
    for c in ("techpix-postgres", "techpix-kafka", "techpix-prometheus",
              "techpix-grafana", "techpix-keycloak"):
        print("   %-20s %s" % (c, "UP" if container_running(c) else "parado"))


def ensure_extras():
    step("containers avulsos: gitea, sonarqube, jfrog")
    for c in ("techpix-gitea", "techpix-sonarqube", "techpix-jfrog"):
        if container_running(c):
            print("   %-20s UP" % c)
        elif container_exists(c):
            run(["docker", "start", c], 60)
            print("   %-20s religado" % c)
        else:
            print("   %-20s nao existe (provisione uma vez: local-git-server / tekton-up / jfrog-up)" % c)


def ensure_kind():
    step("cluster kind techpix")
    if not container_exists("techpix-control-plane"):
        print("   cluster nao existe; rode scripts/k8s-up.ps1 uma vez para cria-lo")
        return
    if not container_running("techpix-control-plane"):
        run(["docker", "start", "techpix-control-plane"], 120)
        print("   control-plane religado")
    run(["kubectl", "config", "use-context", "kind-techpix"], 20)

    def pods_ready():
        rc, out = run(["kubectl", "-n", "techpix-dev", "get", "pods", "--no-headers"], 20)
        if rc != 0 or not out.strip():
            return False
        for line in out.strip().splitlines():
            cols = line.split()
            ready = cols[1].split("/")
            if cols[2] != "Running" or ready[0] != ready[1]:
                return False
        return True

    ok = wait_for("pods de techpix-dev", pods_ready, 48, 5)
    print("   pods de techpix-dev %s" % ("prontos" if ok else "ainda subindo (veja kubectl get pods)"))


def ensure_portforwards():
    step("port-forward: Tekton Dashboard (:9097)")
    if http_ok("http://localhost:9097"):
        print("   ja esta aberto")
        return
    flags = 0x00000008 | 0x00000200 if os.name == "nt" else 0
    subprocess.Popen(
        ["kubectl", "-n", "tekton-pipelines", "port-forward", "svc/tekton-dashboard", "9097:9097"],
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
        creationflags=flags if os.name == "nt" else 0,
        start_new_session=(os.name != "nt"),
    )
    time.sleep(2)
    print("   %s" % ("aberto" if http_ok("http://localhost:9097") else "nao respondeu (Tekton instalado?)"))


def ensure_lab9():
    step("lab Aula 9 (simulador na :8080)")
    if http_ok("http://localhost:8080/actuator/health"):
        print("   ja esta no ar")
        return
    run([sys.executable, LAB9, "reset"], 30)
    run([sys.executable, LAB9, "up"], 60)
    print("   %s" % ("no ar" if http_ok("http://localhost:8080/actuator/health") else "falhou (porta ocupada?)"))


def ensure_painel():
    step("painel do instrutor (:8099)")
    if http_ok("http://localhost:8099"):
        print("   ja esta no ar")
        return
    flags = 0x00000008 | 0x00000200 if os.name == "nt" else 0
    subprocess.Popen(
        [sys.executable, PAINEL],
        cwd=ROOT, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
        creationflags=flags if os.name == "nt" else 0,
        start_new_session=(os.name != "nt"),
    )
    time.sleep(2)
    print("   %s" % ("no ar" if http_ok("http://localhost:8099") else "nao respondeu"))


def summary():
    step("RESUMO")
    checks = [
        ("Painel do instrutor", "http://localhost:8099", "http://localhost:8099"),
        ("Lab Aula 9 (health)", "http://localhost:8080/actuator/health", "http://localhost:8080"),
        ("Monolito dev (kind)", "http://localhost:8090/actuator/health", "http://localhost:8090"),
        ("Fraud dev (kind)", "http://localhost:8091/actuator/health/readiness", "http://localhost:8091"),
        ("Prometheus", "http://localhost:9090/-/ready", "http://localhost:9090"),
        ("Grafana (aula 9)", "http://localhost:3000/api/health", "http://localhost:3000/d/aula9"),
        ("Gitea", "http://localhost:3001", "http://localhost:3001"),
        ("SonarQube", "http://localhost:9000/api/system/status", "http://localhost:9000"),
        ("JFrog Artifactory", "http://localhost:8082/artifactory/api/system/ping", "http://localhost:8082"),
        ("Keycloak", "http://localhost:8180/realms/techpix/.well-known/openid-configuration", "http://localhost:8180"),
        ("Tekton Dashboard", "http://localhost:9097", "http://localhost:9097"),
    ]
    falhas = 0
    for nome, check, url in checks:
        ok = http_ok(check, 6)
        falhas += 0 if ok else 1
        print("   %-22s %-4s %s" % (nome, "UP" if ok else "----", url))
    print()
    if falhas == 0:
        print("Tudo no ar. Painel: http://localhost:8099")
    else:
        print("%d servico(s) ainda fora. SonarQube/JFrog/Keycloak demoram alguns" % falhas)
        print("minutos apos religar; rode 'python scripts/sobe-tudo.py status' de novo.")


def main():
    if len(sys.argv) > 1 and sys.argv[1] == "status":
        summary()
        return
    if not ensure_docker():
        sys.exit(1)
    ensure_compose()
    ensure_extras()
    ensure_kind()
    ensure_portforwards()
    ensure_lab9()
    ensure_painel()
    # os mais lentos (sonar/jfrog/keycloak) terminam de subir durante os passos acima
    summary()


if __name__ == "__main__":
    main()
