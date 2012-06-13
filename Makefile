# Makefile for SimpleEMR
#
# driver for various experiments with simpleEMR application.  
# assumes you have s3cmd and elastic mapreduce commandline utils
# set up and configured with your account access id/secret key
#
# run 
# % make
# to get the list of options.
#
# karnab@amazon.com

#
# commands setup (ADJUST THESE IF NEEDED)
# 
S3CMD                   = s3cmd
EMR			= elastic-mapreduce
CLUSTERSIZE		= 10
REGION                  = us-east-1
KEY			= normal
KEYPATH			= ${HOME}/.ssh/normal.pem

# 
# make targets 
#

help:
	@echo "help for Makefile for SimpleEMR sample project"
	@echo "make create           - create an EMR Cluster with default settings (10 x c1.medium)"
	@echo "make destroy          - clean up everything (terminate cluster and remove s3 bucket)"
	@echo
	@echo "make submitpigjob     - submit a job to the cluster with default settings"
	@echo "make submithivejob    - submit a job with optimized split size"
	@echo
	@echo "make logs             - show the stdout of job"
	@echo "make ssh              - log into head node of cluster"


#
# top level target for removing all derived data
#
clean: cleanbootstrap
	@echo "removed all unnecessary files"
	mvn clean

#
# removes all data copied to s3
#
cleanbootstrap:
	-${S3CMD} -r rb s3://$(USER).twitter.emr/

#
# top level target to tear down cluster and cleanup everything
#
destroy: cleanbootstrap
	@ echo deleting server stack simple.emr
	-${EMR} -j `cat ./jobflowid` --terminate
	-rm ./jobflowid
	-rm ./numberOfMappers


#
# push data into s3 
#

bootstrap: ./target/TwitterEMR-0.1.0-job.jar
	-${S3CMD} mb s3://$(USER).twitter.emr
	${S3CMD} sync --acl-public ./target/TwitterEMR-0.1.0-job.jar  s3://$(USER).twitter.emr
	${S3CMD} sync --acl-public ./pig s3://${USER}.twitter.emr/ 
	${S3CMD} sync --acl-public ./lib s3://${USER}.twitter.emr/ 
	${S3CMD} sync --acl-public ./hive s3://${USER}.twitter.emr/

#
# driver to build source code
#
./target/TwitterEMR-0.1.0-job.jar:
	mvn package

#
# top level target to create a new cluster of c1.mediums
#

create: 
	@ echo creating EMR cluster
	${EMR} elastic-mapreduce --create --alive --name "$(USER)'s Twitter Analytics EMR Cluster" \
	--num-instances ${CLUSTERSIZE} \
	--hive-interactive \
	--instance-type c1.medium | cut -d " " -f 4 > ./jobflowid
	@ echo "24 * (${CLUSTERSIZE} - 1)" | bc  > ./numberOfMappers

submitpigjob: 
	${EMR} -j `cat ./jobflowid` --jar s3://$(USER).twitter.emr/lib/pig-0.9.2-withouthadoop.jar --arg -Dmapred.max.split.size=25000000 --arg -Dio.file.buffer.size=1048576 --arg s3://$(USER).twitter.emr/pig/histogram.pig

submithivejob: 
	${EMR} -j `cat ./jobflowid` --hive-script --args s3://$(USER).twitter.emr/hive/histogram.hive --args -hiveconf,mapred.max.split.size=25000000 --args -hiveconf,io.file.buffer.size=1048576

submitjavajob: 
	${EMR} -j `cat ./jobflowid` --jar s3://$(USER).simple.emr/SimpleEMR-0.1.0-job.jar --arg s3://com.amazon.karan.ebstest/nt2.fs --arg s3://$(USER).simple.emr/output


#
# logs:  use this to see output of jobs
#

logs: 
	${EMR} -j `cat ./jobflowid` --logs


#
# ssh: quick wrapper to ssh into the master node of the cluster
#
ssh:
	${EMR} -j `cat ./jobflowid` --ssh


sshproxy:
	j=`cat ./jobflowid`; h=`${EMR} --describe -j $$j | grep "MasterPublicDnsName" | cut -d "\"" -f 4`; echo "h=$$h"; if [ -z "$$h" ]; then echo "master not provisioned"; exit 1; fi
	j=`cat ./jobflowid`; h=`${EMR} --describe $$j | grep "MasterPublicDnsName" | cut -d "\"" -f 4`; ssh -L 9100:localhost:9100 -i ${KEYPATH} hadoop@$$h


vis:
	 cd ./d3; python -m SimpleHTTPServer 8888

