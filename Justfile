publish:
  xaoc

deploy: publish
  rsync -az -e "ssh -p 26541" --progress output/ready/* flowthing@blog.flowthing.me:/var/www/html/blog/
  rsync -az -e "ssh -p 26541" --progress html/* flowthing@blog.flowthing.me:/var/www/html/blog/

watch:
  find posts | entr xaoc
