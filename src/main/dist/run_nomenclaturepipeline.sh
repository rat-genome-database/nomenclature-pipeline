# Shell script to run the nomenclature pipeline
#
APPHOME=/home/rgddata/pipelines/NomenclaturePipeline
SERVER=`hostname -s | tr '[a-z]' '[A-Z]'`

$APPHOME/_run.sh
