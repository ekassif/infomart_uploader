# InfoMart Export to S3 Uploader
This small utility needs to run as a Windows service. 
It will monitor the folder where Infomart exports the data. When it finds
a new export (directory consisting of three files), it will encrypt it, 
upload it to S3 and then log success or failure information.
<br/>
## Directions:
* The service needs the following environment variable to be defined:
    * **ENV**: The environment name (dev, test, prod etc). It will use this variable to retrieve the other information it needs from the SSM Parameter store, as defined below.
* The following SSM parameters needed for this service to run ($ENV is defined above):
    * **/application/infomart/$ENV/export/s3-bucket**: The S3 bucket name to which this service will upload the infomart export files
    * **/application/infomart/$ENV/export/s3-prefix**: The S3 prefix to use for the files this service will upload to S3. Example "/infomart/exports/"
    * **/application/infomart/$ENV/export/export-dir**: The directory on the windows server to monitor for infomart exports
    * **/application/infomart/$ENV/export/crds-pgp-key**: The encryption key for CRDS
* The EC2 instance profile role has the following permissions:
    * Policy: AmazonSSMReadOnlyAccess. For the code to access param store
    * Policy: AmazonSSMManagedInstanceCore. For SSM to manage the EC2 instance
    * Policy: CloudWatchLogsFullAccess. Or inline policy allowing EC2 to push logs to CloudWatch
    * Policy allowing S3 Read/Write access to the S3 bucket defined in the SSM Parameters
* The 