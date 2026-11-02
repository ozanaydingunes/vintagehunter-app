#!/usr/bin/env bash
# Test çıktısını ci-results dalına yazar (Releases sayfasını kirletmeden okunabilsin diye).
# Kullanım: ci-push-results.sh <klasör> <iş-adı>
set -u
SRC="$1"; JOB="$2"
RUN="${GITHUB_RUN_NUMBER}-${GITHUB_RUN_ATTEMPT}-${GITHUB_REF_NAME//\//_}"
REPO="https://x-access-token:${GH_TOKEN}@github.com/${GITHUB_REPOSITORY}"
WORK=$(mktemp -d)
git config --global user.name "vh-ci"
git config --global user.email "vh-ci@users.noreply.github.com"
if ! git clone -q --depth 1 --branch ci-results "$REPO" "$WORK" 2>/dev/null; then
  rm -rf "$WORK"; mkdir -p "$WORK"; cd "$WORK"; git init -q -b ci-results; git remote add origin "$REPO"; cd - >/dev/null
fi
mkdir -p "$WORK/runs/$RUN/$JOB"
cp -r "$SRC"/. "$WORK/runs/$RUN/$JOB/" 2>/dev/null || true
cd "$WORK"
# yalnızca son 6 çalışmayı tut
ls -1d runs/* 2>/dev/null | sort -t- -k1,1n | head -n -6 | xargs -r git rm -rq --ignore-unmatch
ls -1d runs/* 2>/dev/null | sort -t- -k1,1n | head -n -6 | xargs -r rm -rf
git add -A
git commit -qm "sonuç $RUN $JOB" || exit 0
for i in 1 2 3 4 5; do
  git push -q origin ci-results && exit 0
  git pull -q --rebase origin ci-results || true
  sleep 3
done
exit 1
