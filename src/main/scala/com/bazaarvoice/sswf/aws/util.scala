package com.bazaarvoice.sswf.aws

import com.amazonaws.services.simpleworkflow.AmazonSimpleWorkflow
import com.amazonaws.services.simpleworkflow.model.{ActivityTypeInfo, ActivityTypeInfos, ListActivityTypesRequest, RegistrationStatus}

import scala.collection.JavaConversions._

object util {
  def getActivityTypes(swf: AmazonSimpleWorkflow, domain: String): List[ActivityTypeInfo] = {
    val listActivityTypesRequest = new ListActivityTypesRequest()
       .withDomain(domain)
       .withRegistrationStatus(RegistrationStatus.REGISTERED)

    val iterateFn = (prev: ActivityTypeInfos) =>
      if (prev == null || prev.getNextPageToken == null) null
      else swf.listActivityTypes(listActivityTypesRequest.withNextPageToken(prev.getNextPageToken))

    Stream
       .iterate(swf.listActivityTypes(listActivityTypesRequest))(iterateFn)
       .takeWhile(_ != null)
       .flatten(r => collectionAsScalaIterable(r.getTypeInfos))
       .toList
  }
}
