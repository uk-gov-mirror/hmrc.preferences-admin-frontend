/*
 * Copyright 2026 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.preferencesadminfrontend.services

import org.mockito.Mockito._
import org.scalatest.concurrent.{ IntegrationPatience, ScalaFutures }
import org.scalatestplus.play.PlaySpec
import play.api.http.Status
import play.api.libs.json.Json
import uk.gov.hmrc.http.{ HeaderCarrier, HttpResponse }
import uk.gov.hmrc.preferencesadminfrontend.model._
import uk.gov.hmrc.preferencesadminfrontend.utils.SpecBase

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class MessageServiceSpec extends PlaySpec with ScalaFutures with IntegrationPatience {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  "getGmcBatches" should {

    "return a valid update result" in new MessageServiceTestCase {
      val response = HttpResponse(Status.OK, validGmcBatchSeqResponseJson, Map.empty)
      when(messageConnectorMock.getGmcBatches()).thenReturn(Future.successful(response))
      messageService.getGmcBatches().futureValue mustBe Left(Seq(gmcBatch))
    }

    "return an error message if status is 200 but there no valid batches returned" in new MessageServiceTestCase {
      val responseJson = Json.parse("""[{"blah": "test"}]""".stripMargin)
      val response = HttpResponse(Status.OK, responseJson, Map.empty)
      when(messageConnectorMock.getGmcBatches()).thenReturn(Future.successful(response))
      messageService.getGmcBatches().futureValue mustBe
        Right(
          "The GMC batches retrieved for version v4 do not appear to be valid."
        )
    }

    "return a response body if status isn't 200" in new MessageServiceTestCase {
      val responseForV4 = HttpResponse(Status.NOT_FOUND, validGmcBatchSeqResponseJson, Map.empty)
      when(messageConnectorMock.getGmcBatches()).thenReturn(Future.successful(responseForV4))
      messageService.getGmcBatches().futureValue mustBe
        Right("""[ {
                |  "formId" : "SA359",
                |  "issueDate" : "2017-03-16",
                |  "batchId" : "123456789",
                |  "templateId" : "newMessageAlert_SA359",
                |  "count" : 15778
                |} ]""".stripMargin)
    }

    "return a response body if status isn't 200 for v4 batch is returned" in new MessageServiceTestCase {
      val responseForV4 = HttpResponse(Status.OK, validGmcBatchSeqResponseJson, Map.empty)
      when(messageConnectorMock.getGmcBatches()).thenReturn(Future.successful(responseForV4))
      messageService.getGmcBatches().futureValue mustBe Left(Seq(gmcBatch))
    }
  }

  "getRandomMessagePreview" should {

    "return a valid alert result" in new MessageServiceTestCase {
      val response = HttpResponse(Status.OK, validMessagePreviewResponseJson, Map.empty)
      when(messageConnectorMock.getRandomMessagePreview(gmcBatch)).thenReturn(Future.successful(response))
      messageService.getRandomMessagePreview(gmcBatch).futureValue mustBe Left(
        BatchMessagePreview(
          MessagePreview(
            "Reminder to file a Self Assessment return",
            None,
            "PHA+RGVhciBjdXN0b21lciw8L3A+CjxwPkhNUkMgaXMgb2ZmZXJpbmcgYSByYW5nZSBvZiBzdXBwb3J0IGFoZWFkIG9mIHRoZSBjaGFuZ2VzIGR1ZSBmcm9tIEHigIxwcuKAjGlsIDLigIwwMeKAjDkgYWZmZWN0aW5nIFZBVC1yZWdpc3RlcmVkIGJ1c2luZXNzZXMgd2l0aCBhIHRheGFibGUgdHVybm92ZXIgYWJvdmUgwqM4NSwwMDAuPC9wPgo8cD5IZXJl4oCZcyBhIHNlbGVjdGlvbiBmb3IgeW91LjwvcD4KPHA+PGI+TWFraW5nIFRheCBEaWdpdGFsPC9iPjwvcD4KPHA+UHJvdmlkaW5nIGFuIG92ZXJ2aWV3IG9mIE1ha2luZyBUYXggRGlnaXRhbCwgdGhpcyBsaXZlIHdlYmluYXIgaW5jbHVkZXMgZGlnaXRhbCByZWNvcmQga2VlcGluZywgY29tcGF0aWJsZSBzb2Z0d2FyZSwgc2lnbmluZyB1cCBmb3IgTWFraW5nIFRheCBEaWdpdGFsLCB3b3JraW5nIHdpdGggYWdlbnRzIGFuZCBzdWJtaXR0aW5nIFZBVCByZXR1cm5zIGRpcmVjdGx5IGZyb20gdGhlIGJ1c2luZXNzLjwvcD4KPHA+WW91IGNhbiBhc2sgcXVlc3Rpb25zIHVzaW5nIHRoZSBvbi1zY3JlZW4gdGV4dCBib3guPC9wPgo8cD48YSBocmVmPSJodHRwczovL2xpbmtzLmFkdmljZS5obXJjLmdvdi51ay90cmFjaz90eXBlPWNsaWNrJmVuaWQ9WldGelBURW1iWE5wWkQwbVlYVnBaRDBtYldGcGJHbHVaMmxrUFRJd01Ua3dNekF4TGpJME9ESTNOekVtYldWemMyRm5aV2xrUFUxRVFpMVFVa1F0UWxWTUxUSXdNVGt3TXpBeExqSTBPREkzTnpFbVpHRjBZV0poYzJWcFpEMHhNREF4Sm5ObGNtbGhiRDB4TnpBNE9UYzFNQ1psYldGcGJHbGtQWEp2WW10M1lXeHdiMnhsUUdkdFlXbHNMbU52YlNaMWMyVnlhV1E5Y205aWEzZGhiSEJ2YkdWQVoyMWhhV3d1WTI5dEpuUmhjbWRsZEdsa1BTWm1iRDBtWlhoMGNtRTlUWFZzZEdsMllYSnBZWFJsU1dROUppWW0mJiYxMDEmJiZodHRwczovL2F0dGVuZGVlLmdvdG93ZWJpbmFyLmNvbS9ydC8xNDg4NDY5NzYwMzI2MDI1NzI5P3NvdXJjZT1DYW1wYWlnbi1NYXItMmEiPkNob29zZSBhIGRhdGUgYW5kIHRpbWU8L2E+PC9wPgo8cD5UaGVyZSBhcmUgYWxzbyBzaG9ydCB2aWRlb3Mgb24gb3VyIFlvdVR1YmUgY2hhbm5lbCwgaW5jbHVkaW5nICc8YSBocmVmPSJodHRwczovL2xpbmtzLmFkdmljZS5obXJjLmdvdi51ay90cmFjaz90eXBlPWNsaWNrJmVuaWQ9WldGelBURW1iWE5wWkQwbVlYVnBaRDBtYldGcGJHbHVaMmxrUFRJd01Ua3dNekF4TGpJME9ESTNOekVtYldWemMyRm5aV2xrUFUxRVFpMVFVa1F0UWxWTUxUSXdNVGt3TXpBeExqSTBPREkzTnpFbVpHRjBZV0poYzJWcFpEMHhNREF4Sm5ObGNtbGhiRDB4TnpBNE9UYzFNQ1psYldGcGJHbGtQWEp2WW10M1lXeHdiMnhsUUdkdFlXbHNMbU52YlNaMWMyVnlhV1E5Y205aWEzZGhiSEJ2YkdWQVoyMWhhV3d1WTI5dEpuUmhjbWRsZEdsa1BTWm1iRDBtWlhoMGNtRTlUWFZzZEdsMllYSnBZWFJsU1dROUppWW0mJiYxMDImJiZodHRwczovL3d3dy55b3V0dWJlLmNvbS93YXRjaD92PUhTSGJEaldabDN3JmluZGV4PTQmbGlzdD1QTDhFY25oZUR0MXppMWlwazFxZXhyd2RBVTVPNkxTODRhJnV0bV9zb3VyY2U9SE1SQy1EQ1MtTWFyLTJhJnV0bV9jYW1wYWlnbj1EQ1MtQ2FtcGFpZ24mdXRtX21lZGl1bT1lbWFpbCI+SG93IGRvZXMgTWFraW5nIFRheCBEaWdpdGFsIGZvciBWQVQgYWZmZWN0IHlvdT88L2E+JyBhbmQgJzxhIGhyZWY9Imh0dHBzOi8vbGlua3MuYWR2aWNlLmhtcmMuZ292LnVrL3RyYWNrP3R5cGU9Y2xpY2smZW5pZD1aV0Z6UFRFbWJYTnBaRDBtWVhWcFpEMG1iV0ZwYkdsdVoybGtQVEl3TVRrd016QXhMakkwT0RJM056RW1iV1Z6YzJGblpXbGtQVTFFUWkxUVVrUXRRbFZNTFRJd01Ua3dNekF4TGpJME9ESTNOekVtWkdGMFlXSmhjMlZwWkQweE1EQXhKbk5sY21saGJEMHhOekE0T1RjMU1DWmxiV0ZwYkdsa1BYSnZZbXQzWVd4d2IyeGxRR2R0WVdsc0xtTnZiU1oxYzJWeWFXUTljbTlpYTNkaGJIQnZiR1ZBWjIxaGFXd3VZMjl0Sm5SaGNtZGxkR2xrUFNabWJEMG1aWGgwY21FOVRYVnNkR2wyWVhKcFlYUmxTV1E5SmlZbSYmJjEwMyYmJmh0dHBzOi8vd3d3LnlvdXR1YmUuY29tL3dhdGNoP3Y9a09LRDRrSHZsekkmaW5kZXg9MyZsaXN0PVBMOEVjbmhlRHQxemkxaXBrMXFleHJ3ZEFVNU82TFM4NGEmdXRtX3NvdXJjZT1ITVJDLURDUy1NYXItMmEmdXRtX2NhbXBhaWduPURDUy1DYW1wYWlnbiZ1dG1fbWVkaXVtPWVtYWlsIj5Ib3cgdG8gc2lnbiB1cCBmb3IgTWFraW5nIFRheCBEaWdpdGFsIGZvciBWQVQ/PC9hPicg4oCTIGF2YWlsYWJsZSB0byB2aWV3IGFueXRpbWUuPC9wPgo8cD5WaXNpdCBITVJD4oCZcyA8YSBocmVmPSJodHRwczovL2xpbmtzLmFkdmljZS5obXJjLmdvdi51ay90cmFjaz90eXBlPWNsaWNrJmVuaWQ9WldGelBURW1iWE5wWkQwbVlYVnBaRDBtYldGcGJHbHVaMmxrUFRJd01Ua3dNekF4TGpJME9ESTNOekVtYldWemMyRm5aV2xrUFUxRVFpMVFVa1F0UWxWTUxUSXdNVGt3TXpBeExqSTBPREkzTnpFbVpHRjBZV0poYzJWcFpEMHhNREF4Sm5ObGNtbGhiRDB4TnpBNE9UYzFNQ1psYldGcGJHbGtQWEp2WW10M1lXeHdiMnhsUUdkdFlXbHNMbU52YlNaMWMyVnlhV1E5Y205aWEzZGhiSEJ2YkdWQVoyMWhhV3d1WTI5dEpuUmhjbWRsZEdsa1BTWm1iRDBtWlhoMGNtRTlUWFZzZEdsMllYSnBZWFJsU1dROUppWW0mJiYxMDQmJiZodHRwczovL29ubGluZS5obXJjLmdvdi51ay93ZWJjaGF0cHJvZC9jb21tdW5pdHkvZm9ydW1zL3Nob3cvMTAzLnBhZ2UiPk9ubGluZSBDdXN0b21lciBGb3J1bTwvYT4gaWYgeW914oCZdmUgZ290IGEgcXVlc3Rpb24gYWJvdXQgTWFraW5nIFRheCBEaWdpdGFsIOKAkyBzZWUgd2hhdCBvdGhlcnMgYXJlIHRhbGtpbmcgYWJvdXQsIGFzayB5b3VyIG93biBxdWVzdGlvbnMgYW5kIGdldCBhbnN3ZXJzIGZyb20gdGhlIGV4cGVydHMuPC9wPgo8cD5ITVJDIG9ubGluZSBndWlkYW5jZSDigJMgaGVscGluZyB5b3UgZ2V0IGl0IHJpZ2h0LjwvcD4KPHA+QWxpc29uIFdhbHNoPC9wPgo8cD5IZWFkIG9mIERpZ2l0YWwgQ29tbXVuaWNhdGlvbiBTZXJ2aWNlczwvcD4=",
            None,
            "9834763878934",
            "mailout-batch",
            "05 APR 2019",
            "sautr"
          ),
          "123456789"
        )
      )
    }

    "return an error message if status is 200 but there no valid message preview returned" in new MessageServiceTestCase {
      val responseJson = Json.parse("""[{"blah": "test"}]""".stripMargin)
      val response = HttpResponse(Status.OK, responseJson, Map.empty)
      when(messageConnectorMock.getRandomMessagePreview(gmcBatch)).thenReturn(Future.successful(response))
      messageService.getRandomMessagePreview(gmcBatch).futureValue mustBe
        Right("The message preview retrieved does not appear to be valid.")
    }

    "return a response body if status isn't 200" in new MessageServiceTestCase {
      val response = HttpResponse(Status.NOT_FOUND, validMessagePreviewResponseJson, Map.empty)
      when(messageConnectorMock.getRandomMessagePreview(gmcBatch)).thenReturn(Future.successful(response))
      messageService.getRandomMessagePreview(gmcBatch).futureValue mustBe
        Right(
          """{
            |  "subject" : "Reminder to file a Self Assessment return",
            |  "content" : "PHA+RGVhciBjdXN0b21lciw8L3A+CjxwPkhNUkMgaXMgb2ZmZXJpbmcgYSByYW5nZSBvZiBzdXBwb3J0IGFoZWFkIG9mIHRoZSBjaGFuZ2VzIGR1ZSBmcm9tIEHigIxwcuKAjGlsIDLigIwwMeKAjDkgYWZmZWN0aW5nIFZBVC1yZWdpc3RlcmVkIGJ1c2luZXNzZXMgd2l0aCBhIHRheGFibGUgdHVybm92ZXIgYWJvdmUgwqM4NSwwMDAuPC9wPgo8cD5IZXJl4oCZcyBhIHNlbGVjdGlvbiBmb3IgeW91LjwvcD4KPHA+PGI+TWFraW5nIFRheCBEaWdpdGFsPC9iPjwvcD4KPHA+UHJvdmlkaW5nIGFuIG92ZXJ2aWV3IG9mIE1ha2luZyBUYXggRGlnaXRhbCwgdGhpcyBsaXZlIHdlYmluYXIgaW5jbHVkZXMgZGlnaXRhbCByZWNvcmQga2VlcGluZywgY29tcGF0aWJsZSBzb2Z0d2FyZSwgc2lnbmluZyB1cCBmb3IgTWFraW5nIFRheCBEaWdpdGFsLCB3b3JraW5nIHdpdGggYWdlbnRzIGFuZCBzdWJtaXR0aW5nIFZBVCByZXR1cm5zIGRpcmVjdGx5IGZyb20gdGhlIGJ1c2luZXNzLjwvcD4KPHA+WW91IGNhbiBhc2sgcXVlc3Rpb25zIHVzaW5nIHRoZSBvbi1zY3JlZW4gdGV4dCBib3guPC9wPgo8cD48YSBocmVmPSJodHRwczovL2xpbmtzLmFkdmljZS5obXJjLmdvdi51ay90cmFjaz90eXBlPWNsaWNrJmVuaWQ9WldGelBURW1iWE5wWkQwbVlYVnBaRDBtYldGcGJHbHVaMmxrUFRJd01Ua3dNekF4TGpJME9ESTNOekVtYldWemMyRm5aV2xrUFUxRVFpMVFVa1F0UWxWTUxUSXdNVGt3TXpBeExqSTBPREkzTnpFbVpHRjBZV0poYzJWcFpEMHhNREF4Sm5ObGNtbGhiRDB4TnpBNE9UYzFNQ1psYldGcGJHbGtQWEp2WW10M1lXeHdiMnhsUUdkdFlXbHNMbU52YlNaMWMyVnlhV1E5Y205aWEzZGhiSEJ2YkdWQVoyMWhhV3d1WTI5dEpuUmhjbWRsZEdsa1BTWm1iRDBtWlhoMGNtRTlUWFZzZEdsMllYSnBZWFJsU1dROUppWW0mJiYxMDEmJiZodHRwczovL2F0dGVuZGVlLmdvdG93ZWJpbmFyLmNvbS9ydC8xNDg4NDY5NzYwMzI2MDI1NzI5P3NvdXJjZT1DYW1wYWlnbi1NYXItMmEiPkNob29zZSBhIGRhdGUgYW5kIHRpbWU8L2E+PC9wPgo8cD5UaGVyZSBhcmUgYWxzbyBzaG9ydCB2aWRlb3Mgb24gb3VyIFlvdVR1YmUgY2hhbm5lbCwgaW5jbHVkaW5nICc8YSBocmVmPSJodHRwczovL2xpbmtzLmFkdmljZS5obXJjLmdvdi51ay90cmFjaz90eXBlPWNsaWNrJmVuaWQ9WldGelBURW1iWE5wWkQwbVlYVnBaRDBtYldGcGJHbHVaMmxrUFRJd01Ua3dNekF4TGpJME9ESTNOekVtYldWemMyRm5aV2xrUFUxRVFpMVFVa1F0UWxWTUxUSXdNVGt3TXpBeExqSTBPREkzTnpFbVpHRjBZV0poYzJWcFpEMHhNREF4Sm5ObGNtbGhiRDB4TnpBNE9UYzFNQ1psYldGcGJHbGtQWEp2WW10M1lXeHdiMnhsUUdkdFlXbHNMbU52YlNaMWMyVnlhV1E5Y205aWEzZGhiSEJ2YkdWQVoyMWhhV3d1WTI5dEpuUmhjbWRsZEdsa1BTWm1iRDBtWlhoMGNtRTlUWFZzZEdsMllYSnBZWFJsU1dROUppWW0mJiYxMDImJiZodHRwczovL3d3dy55b3V0dWJlLmNvbS93YXRjaD92PUhTSGJEaldabDN3JmluZGV4PTQmbGlzdD1QTDhFY25oZUR0MXppMWlwazFxZXhyd2RBVTVPNkxTODRhJnV0bV9zb3VyY2U9SE1SQy1EQ1MtTWFyLTJhJnV0bV9jYW1wYWlnbj1EQ1MtQ2FtcGFpZ24mdXRtX21lZGl1bT1lbWFpbCI+SG93IGRvZXMgTWFraW5nIFRheCBEaWdpdGFsIGZvciBWQVQgYWZmZWN0IHlvdT88L2E+JyBhbmQgJzxhIGhyZWY9Imh0dHBzOi8vbGlua3MuYWR2aWNlLmhtcmMuZ292LnVrL3RyYWNrP3R5cGU9Y2xpY2smZW5pZD1aV0Z6UFRFbWJYTnBaRDBtWVhWcFpEMG1iV0ZwYkdsdVoybGtQVEl3TVRrd016QXhMakkwT0RJM056RW1iV1Z6YzJGblpXbGtQVTFFUWkxUVVrUXRRbFZNTFRJd01Ua3dNekF4TGpJME9ESTNOekVtWkdGMFlXSmhjMlZwWkQweE1EQXhKbk5sY21saGJEMHhOekE0T1RjMU1DWmxiV0ZwYkdsa1BYSnZZbXQzWVd4d2IyeGxRR2R0WVdsc0xtTnZiU1oxYzJWeWFXUTljbTlpYTNkaGJIQnZiR1ZBWjIxaGFXd3VZMjl0Sm5SaGNtZGxkR2xrUFNabWJEMG1aWGgwY21FOVRYVnNkR2wyWVhKcFlYUmxTV1E5SmlZbSYmJjEwMyYmJmh0dHBzOi8vd3d3LnlvdXR1YmUuY29tL3dhdGNoP3Y9a09LRDRrSHZsekkmaW5kZXg9MyZsaXN0PVBMOEVjbmhlRHQxemkxaXBrMXFleHJ3ZEFVNU82TFM4NGEmdXRtX3NvdXJjZT1ITVJDLURDUy1NYXItMmEmdXRtX2NhbXBhaWduPURDUy1DYW1wYWlnbiZ1dG1fbWVkaXVtPWVtYWlsIj5Ib3cgdG8gc2lnbiB1cCBmb3IgTWFraW5nIFRheCBEaWdpdGFsIGZvciBWQVQ/PC9hPicg4oCTIGF2YWlsYWJsZSB0byB2aWV3IGFueXRpbWUuPC9wPgo8cD5WaXNpdCBITVJD4oCZcyA8YSBocmVmPSJodHRwczovL2xpbmtzLmFkdmljZS5obXJjLmdvdi51ay90cmFjaz90eXBlPWNsaWNrJmVuaWQ9WldGelBURW1iWE5wWkQwbVlYVnBaRDBtYldGcGJHbHVaMmxrUFRJd01Ua3dNekF4TGpJME9ESTNOekVtYldWemMyRm5aV2xrUFUxRVFpMVFVa1F0UWxWTUxUSXdNVGt3TXpBeExqSTBPREkzTnpFbVpHRjBZV0poYzJWcFpEMHhNREF4Sm5ObGNtbGhiRDB4TnpBNE9UYzFNQ1psYldGcGJHbGtQWEp2WW10M1lXeHdiMnhsUUdkdFlXbHNMbU52YlNaMWMyVnlhV1E5Y205aWEzZGhiSEJ2YkdWQVoyMWhhV3d1WTI5dEpuUmhjbWRsZEdsa1BTWm1iRDBtWlhoMGNtRTlUWFZzZEdsMllYSnBZWFJsU1dROUppWW0mJiYxMDQmJiZodHRwczovL29ubGluZS5obXJjLmdvdi51ay93ZWJjaGF0cHJvZC9jb21tdW5pdHkvZm9ydW1zL3Nob3cvMTAzLnBhZ2UiPk9ubGluZSBDdXN0b21lciBGb3J1bTwvYT4gaWYgeW914oCZdmUgZ290IGEgcXVlc3Rpb24gYWJvdXQgTWFraW5nIFRheCBEaWdpdGFsIOKAkyBzZWUgd2hhdCBvdGhlcnMgYXJlIHRhbGtpbmcgYWJvdXQsIGFzayB5b3VyIG93biBxdWVzdGlvbnMgYW5kIGdldCBhbnN3ZXJzIGZyb20gdGhlIGV4cGVydHMuPC9wPgo8cD5ITVJDIG9ubGluZSBndWlkYW5jZSDigJMgaGVscGluZyB5b3UgZ2V0IGl0IHJpZ2h0LjwvcD4KPHA+QWxpc29uIFdhbHNoPC9wPgo8cD5IZWFkIG9mIERpZ2l0YWwgQ29tbXVuaWNhdGlvbiBTZXJ2aWNlczwvcD4=",
            |  "externalRefId" : "9834763878934",
            |  "messageType" : "mailout-batch",
            |  "issueDate" : "05 APR 2019",
            |  "taxIdentifierName" : "sautr"
            |}""".stripMargin
        )
    }
  }

  trait MessageServiceTestCase extends SpecBase {
    val gmcBatch = GmcBatch(
      "123456789",
      "SA359",
      "2017-03-16",
      "newMessageAlert_SA359",
      Some(15778)
    )

    val validGmcBatchSeqResponseJson = Json.parse("""
        [
            {
                "formId": "SA359",
                "issueDate": "2017-03-16",
                "batchId": "123456789",
                "templateId": "newMessageAlert_SA359",
                "count": 15778
            }
        ]
      """.stripMargin)

    val validMessagePreviewResponseJson = Json.parse(
      """
        |{
        | "subject":"Reminder to file a Self Assessment return",
        | "content":"PHA+RGVhciBjdXN0b21lciw8L3A+CjxwPkhNUkMgaXMgb2ZmZXJpbmcgYSByYW5nZSBvZiBzdXBwb3J0IGFoZWFkIG9mIHRoZSBjaGFuZ2VzIGR1ZSBmcm9tIEHigIxwcuKAjGlsIDLigIwwMeKAjDkgYWZmZWN0aW5nIFZBVC1yZWdpc3RlcmVkIGJ1c2luZXNzZXMgd2l0aCBhIHRheGFibGUgdHVybm92ZXIgYWJvdmUgwqM4NSwwMDAuPC9wPgo8cD5IZXJl4oCZcyBhIHNlbGVjdGlvbiBmb3IgeW91LjwvcD4KPHA+PGI+TWFraW5nIFRheCBEaWdpdGFsPC9iPjwvcD4KPHA+UHJvdmlkaW5nIGFuIG92ZXJ2aWV3IG9mIE1ha2luZyBUYXggRGlnaXRhbCwgdGhpcyBsaXZlIHdlYmluYXIgaW5jbHVkZXMgZGlnaXRhbCByZWNvcmQga2VlcGluZywgY29tcGF0aWJsZSBzb2Z0d2FyZSwgc2lnbmluZyB1cCBmb3IgTWFraW5nIFRheCBEaWdpdGFsLCB3b3JraW5nIHdpdGggYWdlbnRzIGFuZCBzdWJtaXR0aW5nIFZBVCByZXR1cm5zIGRpcmVjdGx5IGZyb20gdGhlIGJ1c2luZXNzLjwvcD4KPHA+WW91IGNhbiBhc2sgcXVlc3Rpb25zIHVzaW5nIHRoZSBvbi1zY3JlZW4gdGV4dCBib3guPC9wPgo8cD48YSBocmVmPSJodHRwczovL2xpbmtzLmFkdmljZS5obXJjLmdvdi51ay90cmFjaz90eXBlPWNsaWNrJmVuaWQ9WldGelBURW1iWE5wWkQwbVlYVnBaRDBtYldGcGJHbHVaMmxrUFRJd01Ua3dNekF4TGpJME9ESTNOekVtYldWemMyRm5aV2xrUFUxRVFpMVFVa1F0UWxWTUxUSXdNVGt3TXpBeExqSTBPREkzTnpFbVpHRjBZV0poYzJWcFpEMHhNREF4Sm5ObGNtbGhiRDB4TnpBNE9UYzFNQ1psYldGcGJHbGtQWEp2WW10M1lXeHdiMnhsUUdkdFlXbHNMbU52YlNaMWMyVnlhV1E5Y205aWEzZGhiSEJ2YkdWQVoyMWhhV3d1WTI5dEpuUmhjbWRsZEdsa1BTWm1iRDBtWlhoMGNtRTlUWFZzZEdsMllYSnBZWFJsU1dROUppWW0mJiYxMDEmJiZodHRwczovL2F0dGVuZGVlLmdvdG93ZWJpbmFyLmNvbS9ydC8xNDg4NDY5NzYwMzI2MDI1NzI5P3NvdXJjZT1DYW1wYWlnbi1NYXItMmEiPkNob29zZSBhIGRhdGUgYW5kIHRpbWU8L2E+PC9wPgo8cD5UaGVyZSBhcmUgYWxzbyBzaG9ydCB2aWRlb3Mgb24gb3VyIFlvdVR1YmUgY2hhbm5lbCwgaW5jbHVkaW5nICc8YSBocmVmPSJodHRwczovL2xpbmtzLmFkdmljZS5obXJjLmdvdi51ay90cmFjaz90eXBlPWNsaWNrJmVuaWQ9WldGelBURW1iWE5wWkQwbVlYVnBaRDBtYldGcGJHbHVaMmxrUFRJd01Ua3dNekF4TGpJME9ESTNOekVtYldWemMyRm5aV2xrUFUxRVFpMVFVa1F0UWxWTUxUSXdNVGt3TXpBeExqSTBPREkzTnpFbVpHRjBZV0poYzJWcFpEMHhNREF4Sm5ObGNtbGhiRDB4TnpBNE9UYzFNQ1psYldGcGJHbGtQWEp2WW10M1lXeHdiMnhsUUdkdFlXbHNMbU52YlNaMWMyVnlhV1E5Y205aWEzZGhiSEJ2YkdWQVoyMWhhV3d1WTI5dEpuUmhjbWRsZEdsa1BTWm1iRDBtWlhoMGNtRTlUWFZzZEdsMllYSnBZWFJsU1dROUppWW0mJiYxMDImJiZodHRwczovL3d3dy55b3V0dWJlLmNvbS93YXRjaD92PUhTSGJEaldabDN3JmluZGV4PTQmbGlzdD1QTDhFY25oZUR0MXppMWlwazFxZXhyd2RBVTVPNkxTODRhJnV0bV9zb3VyY2U9SE1SQy1EQ1MtTWFyLTJhJnV0bV9jYW1wYWlnbj1EQ1MtQ2FtcGFpZ24mdXRtX21lZGl1bT1lbWFpbCI+SG93IGRvZXMgTWFraW5nIFRheCBEaWdpdGFsIGZvciBWQVQgYWZmZWN0IHlvdT88L2E+JyBhbmQgJzxhIGhyZWY9Imh0dHBzOi8vbGlua3MuYWR2aWNlLmhtcmMuZ292LnVrL3RyYWNrP3R5cGU9Y2xpY2smZW5pZD1aV0Z6UFRFbWJYTnBaRDBtWVhWcFpEMG1iV0ZwYkdsdVoybGtQVEl3TVRrd016QXhMakkwT0RJM056RW1iV1Z6YzJGblpXbGtQVTFFUWkxUVVrUXRRbFZNTFRJd01Ua3dNekF4TGpJME9ESTNOekVtWkdGMFlXSmhjMlZwWkQweE1EQXhKbk5sY21saGJEMHhOekE0T1RjMU1DWmxiV0ZwYkdsa1BYSnZZbXQzWVd4d2IyeGxRR2R0WVdsc0xtTnZiU1oxYzJWeWFXUTljbTlpYTNkaGJIQnZiR1ZBWjIxaGFXd3VZMjl0Sm5SaGNtZGxkR2xrUFNabWJEMG1aWGgwY21FOVRYVnNkR2wyWVhKcFlYUmxTV1E5SmlZbSYmJjEwMyYmJmh0dHBzOi8vd3d3LnlvdXR1YmUuY29tL3dhdGNoP3Y9a09LRDRrSHZsekkmaW5kZXg9MyZsaXN0PVBMOEVjbmhlRHQxemkxaXBrMXFleHJ3ZEFVNU82TFM4NGEmdXRtX3NvdXJjZT1ITVJDLURDUy1NYXItMmEmdXRtX2NhbXBhaWduPURDUy1DYW1wYWlnbiZ1dG1fbWVkaXVtPWVtYWlsIj5Ib3cgdG8gc2lnbiB1cCBmb3IgTWFraW5nIFRheCBEaWdpdGFsIGZvciBWQVQ/PC9hPicg4oCTIGF2YWlsYWJsZSB0byB2aWV3IGFueXRpbWUuPC9wPgo8cD5WaXNpdCBITVJD4oCZcyA8YSBocmVmPSJodHRwczovL2xpbmtzLmFkdmljZS5obXJjLmdvdi51ay90cmFjaz90eXBlPWNsaWNrJmVuaWQ9WldGelBURW1iWE5wWkQwbVlYVnBaRDBtYldGcGJHbHVaMmxrUFRJd01Ua3dNekF4TGpJME9ESTNOekVtYldWemMyRm5aV2xrUFUxRVFpMVFVa1F0UWxWTUxUSXdNVGt3TXpBeExqSTBPREkzTnpFbVpHRjBZV0poYzJWcFpEMHhNREF4Sm5ObGNtbGhiRDB4TnpBNE9UYzFNQ1psYldGcGJHbGtQWEp2WW10M1lXeHdiMnhsUUdkdFlXbHNMbU52YlNaMWMyVnlhV1E5Y205aWEzZGhiSEJ2YkdWQVoyMWhhV3d1WTI5dEpuUmhjbWRsZEdsa1BTWm1iRDBtWlhoMGNtRTlUWFZzZEdsMllYSnBZWFJsU1dROUppWW0mJiYxMDQmJiZodHRwczovL29ubGluZS5obXJjLmdvdi51ay93ZWJjaGF0cHJvZC9jb21tdW5pdHkvZm9ydW1zL3Nob3cvMTAzLnBhZ2UiPk9ubGluZSBDdXN0b21lciBGb3J1bTwvYT4gaWYgeW914oCZdmUgZ290IGEgcXVlc3Rpb24gYWJvdXQgTWFraW5nIFRheCBEaWdpdGFsIOKAkyBzZWUgd2hhdCBvdGhlcnMgYXJlIHRhbGtpbmcgYWJvdXQsIGFzayB5b3VyIG93biBxdWVzdGlvbnMgYW5kIGdldCBhbnN3ZXJzIGZyb20gdGhlIGV4cGVydHMuPC9wPgo8cD5ITVJDIG9ubGluZSBndWlkYW5jZSDigJMgaGVscGluZyB5b3UgZ2V0IGl0IHJpZ2h0LjwvcD4KPHA+QWxpc29uIFdhbHNoPC9wPgo8cD5IZWFkIG9mIERpZ2l0YWwgQ29tbXVuaWNhdGlvbiBTZXJ2aWNlczwvcD4=",
        | "externalRefId":"9834763878934",
        | "messageType":"mailout-batch",
        | "issueDate":"05 APR 2019",
        | "taxIdentifierName":"sautr"
        |}
      """.stripMargin
    )

    val messageService = new MessageService(messageConnectorMock)
  }

}
