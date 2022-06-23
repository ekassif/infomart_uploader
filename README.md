# InfoMart Export to S3 Uploader
This small utility needs to run as a Windows service. 
It will monitor the folder where Infomart exports the data. When it finds
a new export (directory consisting of three files), it will encrypt it, 
upload it to S3 and then log success or failure information.
<br/>

## Prerequistes:
* An S3 bucket must exist for the code to upload files to. A single prefix (e.g. /crds) should be used
* The PGP public key for CRDS must be stored in SSM as specified in the "directions" below
## Directions For running the code:
* The service needs the following environment variable to be defined:
    * **infomart.env**: The environment name (dev, test, prod etc). It will use this variable to retrieve the other information it needs from the SSM Parameter store, as defined below. For example, for "dev", 
      an environment property infomart.env=dev should be defined.
      The service will use this to load SSM Parameters replacing $ENV with "dev", so
      /application/infomart/dev/export/* will be the path used.
    * **infomart.aws_region**: OPTIONAL. The AWS region to use. If not present, the service will use the EC2 metadata to retrieve it.
    * **infomart.use_ssm**: OPTIONAL with default=true: If "false", you must provide all necessary parameters using ENV variables. This is for testing purposes only.
* The following SSM parameters needed for this service to run ($ENV is defined above):
    * **/application/infomart/$ENV/export/s3-bucket**: The S3 bucket name to which this service will upload the infomart export files
    * **/application/infomart/$ENV/export/s3-crds-prefix**: The S3 prefix to use for the files this service will upload to S3. Example "/crds/"
    * **/application/infomart/$ENV/export/s3-infomart-prefix**: The S3 prefix to use for the files not PGP encrypted. Example "/infomart/"
    * **/application/infomart/$ENV/export/export-dir**: The directory on the windows server to monitor for infomart exports
    * **/application/infomart/$ENV/export/crds-pgp-key**: The encryption key for CRDS
    * **/application/infomart/$ENV/export/temp-dir**: The temporary directory to store the zip/encrypted files on EC2
* The EC2 instance profile role has the following permissions:
    * Policy: AmazonSSMReadOnlyAccess. For the code to access param store
    * Policy: AmazonSSMManagedInstanceCore. For SSM to manage the EC2 instance
    * Policy: CloudWatchLogsFullAccess. Or inline policy allowing EC2 to push logs to CloudWatch
    * Policy allowing S3 Read/Write access to the S3 bucket defined in the SSM Parameters
* For CodeDeploy to work, the code deploy agent must be installed and running on the EC2 instance
* CloudWatch (unified) agent must be running and configured to collect and send the Spring boot log files to cloud watch