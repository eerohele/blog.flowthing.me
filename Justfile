posts:
  xaoc

drafts:
  xaoc --input drafts --output output/drafts

deploy: posts
  rsync -az -e "ssh -p 26541" --progress output/posts/* flowthing@blog.flowthing.me:/var/www/html/blog/

watch:
  find drafts | entr xaoc --input drafts --output output/drafts
