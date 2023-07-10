# MMS5 Load Service

This service helps large MMS 5 load operations by saving a large file to s3 compliant storage, and returning the url for MMS 5 layer 1 service to issue a SPARQL LOAD operation on. (It also depends on the performance of the underlying quad store load operation)

For usage, see [MMS 5 Docs](https://mms5-deployment-guide.readthedocs.io/en/latest/)
