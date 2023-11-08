[![CircleCI](https://circleci.com/gh/Open-MBEE/flexo-mms-store-service.svg?style=shield)](https://circleci.com/gh/Open-MBEE/flexo-mms-store-service)


# Flexo MMS Store Service

This service helps large Flexo MMS load operations by saving a large file to s3 compliant storage, and returning the url for Flexo MMS layer 1 service to issue a SPARQL LOAD operation on. (It also depends on the performance of the underlying quad store load operation)

For usage, see [Flexo MMS Docs](https://flexo-mms-deployment-guide.readthedocs.io/en/latest/)
