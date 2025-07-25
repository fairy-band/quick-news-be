#!/bin/bash

# Let's Encrypt를 사용한 SSL 인증서 설정

DOMAIN="your-domain.com"
EMAIL="your-email@gmail.com"

echo "🔐 SSL 인증서 설정 시작..."

# Certbot 설치
echo "📦 Certbot 설치 중..."
sudo apt update
sudo apt install snapd -y
sudo snap install core; sudo snap refresh core
sudo snap install --classic certbot

# certbot 명령어 링크 생성
sudo ln -s /snap/bin/certbot /usr/bin/certbot

# 임시로 nginx 중지
echo "⏸️  Nginx 컨테이너 임시 중지..."
docker-compose -f docker-compose.prod.yml stop nginx

# SSL 인증서 발급
echo "🔒 SSL 인증서 발급 중..."
sudo certbot certonly --standalone \
    --email $EMAIL \
    --agree-tos \
    --no-eff-email \
    -d $DOMAIN \
    -d www.$DOMAIN

# SSL 디렉토리 생성 및 복사
echo "📁 SSL 파일 복사 중..."
sudo mkdir -p ./ssl
sudo cp /etc/letsencrypt/live/$DOMAIN/fullchain.pem ./ssl/
sudo cp /etc/letsencrypt/live/$DOMAIN/privkey.pem ./ssl/
sudo chown -R $USER:$USER ./ssl

# nginx.conf에서 도메인 이름 업데이트
sed -i "s/your-domain.com/$DOMAIN/g" nginx.conf

# Nginx 재시작
echo "🔄 Nginx 재시작 중..."
docker-compose -f docker-compose.prod.yml up -d nginx

echo "✅ SSL 인증서 설정 완료!"
echo "🌐 HTTPS URL: https://$DOMAIN"

# 자동 갱신 설정
echo "🔄 SSL 인증서 자동 갱신 설정 중..."
(crontab -l 2>/dev/null; echo "0 12 * * * /usr/bin/certbot renew --quiet && docker-compose -f $PWD/docker-compose.prod.yml restart nginx") | crontab -

echo "📅 자동 갱신 크론탭 설정 완료!"
