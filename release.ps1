$v = git rev-list --count HEAD
git tag v$v -m v$v
git push origin v$v
