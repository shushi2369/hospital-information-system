# nginx add_header 继承规则：location 内出现任意 add_header 后，server 级头全部失效，
# 因此安全头以 include 片段在每个有 add_header 的 location 重复引用。
add_header X-Frame-Options "SAMEORIGIN" always;
add_header X-Content-Type-Options "nosniff" always;
add_header Referrer-Policy "strict-origin-when-cross-origin" always;
add_header Content-Security-Policy "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'" always;
