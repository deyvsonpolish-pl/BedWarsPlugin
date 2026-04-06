@echo off

set /p version=Podaj wersje (np. 1.0.1): 

echo 🔨 Budowanie...
mvn clean package

echo 📦 Commit...
git add .
git commit -m "Release v%version%"

echo 🏷️ Tag...
git tag v%version%

echo 🚀 Push...
git push origin master
git push origin v%version%

echo ✅ GOTOWE! Release sie robi na GitHubie
pause