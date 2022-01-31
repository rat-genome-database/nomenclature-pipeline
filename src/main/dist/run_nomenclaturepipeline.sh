# Shell script to run the nomenclature pipeline
#
APPHOME=/home/rgddata/pipelines/NomenclaturePipeline
SERVER=`hostname -s | tr '[a-z]' '[A-Z]'`

$APPHOME/_run.sh > $APPHOME/run.log 2>&1

#send summary email
EMAIL=mtutaj@mcw.edu
if [ "$SERVER" = "REED" ]; then
  EMAIL_LIST=rgd.devops@mcw.edu,jrsmith@mcw.edu,slaulederkind@mcw.edu
fi
mailx -s "[$SERVER] NomenclaturePipeline ok" $EMAIL < ${APPHOME}/logs/summary.log

