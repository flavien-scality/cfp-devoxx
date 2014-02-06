/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2013 Association du Paris Java User Group.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package controllers

import play.api.mvc.Controller
import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.googleapis.batch.BatchRequest
import com.google.api.client.googleapis.batch.json.JsonBatchCallback
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.googleapis.json.GoogleJsonError
import com.google.api.client.http.HttpHeaders
import com.google.api.client.http.HttpTransport
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.DateTime
import com.google.api.client.util.Lists
import com.google.api.client.util.store.DataStoreFactory
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.calendar.CalendarScopes
import com.google.api.services.calendar.model.Calendar
import com.google.api.services.calendar.model.CalendarList
import com.google.api.services.calendar.model.Event
import com.google.api.services.calendar.model.EventDateTime
import com.google.api.services.calendar.model.Events

import java.io.{File, FileInputStream, IOException, InputStreamReader}
import java.util.Collections
import java.util.Date
import java.util.TimeZone

import scala.collection.JavaConversions._
import org.apache.commons.lang3.RandomStringUtils
import library.calendar.CalendarSample
import play.api.data.Forms._
import models.{Slot, Speaker}

/**
 * Controller created to build and to export the Program.
 *
 * Created by nicolas on 20/01/2014.
 */
object ProgramBuilder extends Controller with Secured {

  val APPLICATION_NAME = "DevoxxFrance_CFP"
  /** Directory to store user credentials. */
  val DATA_STORE_DIR: java.io.File = new java.io.File(System.getProperty("user.home"), ".store/calendar_sample")

  private var dataStoreFactory: FileDataStoreFactory = null
  /** Global instance of the HTTP transport. */
  private var httpTransport: HttpTransport = null
  /** Global instance of the JSON factory. */
  private final val JSON_FACTORY: JsonFactory = JacksonFactory.getDefaultInstance
  private var client: com.google.api.services.calendar.Calendar = null

  def index = IsMemberOf(List("admin")) {
    implicit uuid: String =>
      implicit request =>
        Ok(views.html.ProgramBuilder.index())
  }

  def listCalendars() = IsMemberOf("admin") {
    implicit uuid =>
      implicit request =>

      // initialize the transport
        httpTransport = GoogleNetHttpTransport.newTrustedTransport

        // initialize the data store factory
        dataStoreFactory = new FileDataStoreFactory(DATA_STORE_DIR)

        // authorization
        val credential: Credential = authorize

        // set up global Calendar instance
        client = new com.google.api.services.calendar.Calendar.Builder(httpTransport, JSON_FACTORY, credential).setApplicationName(APPLICATION_NAME).build

        val calendarLists: CalendarList = client.calendarList.list.execute
        val listOfCalendars = calendarLists.getItems.toList.filterNot(_.getId() == "nmartignole@gmail.com").filterNot(_.getId() == "vdgasl0hs6tv1lni0bnk03umj4@group.calendar.google.com")
        Ok(views.html.ProgramBuilder.listCalendars(listOfCalendars))

  }

  val newCalendar = play.api.data.Form(
    tuple(
      "summary_cal" -> nonEmptyText(maxLength = 100),
      "description_cal" -> nonEmptyText(maxLength = 350)
    ))


  def prepareNewCalendar() = IsMemberOf("admin") {
    implicit uuid =>
      implicit request =>
        Ok(views.html.ProgramBuilder.prepareNewCalendar(newCalendar))
  }

  def createCalendar() = IsMemberOf("admin") {
    implicit uuid: String =>
      implicit request =>

        newCalendar.bindFromRequest().fold(hasErrors => BadRequest(views.html.ProgramBuilder.prepareNewCalendar(hasErrors)),
          validNewCalendar => {
            // initialize the transport
            httpTransport = GoogleNetHttpTransport.newTrustedTransport

            // initialize the data store factory
            dataStoreFactory = new FileDataStoreFactory(DATA_STORE_DIR)

            // authorization
            val credential: Credential = authorize

            // set up global Calendar instance
            client = new com.google.api.services.calendar.Calendar.Builder(httpTransport, JSON_FACTORY, credential).setApplicationName(APPLICATION_NAME).build

            val newCalendar = new Calendar()
            newCalendar.setSummary(validNewCalendar._1)
            newCalendar.setDescription(validNewCalendar._2)
            newCalendar.setLocation("Paris")
            newCalendar.setTimeZone("Europe/Paris")
            val createdCalendar = client.calendars().insert(newCalendar).execute()

            Redirect(routes.ProgramBuilder.listCalendars()).flashing("success"->("Created new calendar "+createdCalendar.getId()))
          })
  }

  def deleteCalendar(calendarId: String) = IsMemberOf("admin") {
    implicit uuid: String =>
      implicit request =>

      // initialize the transport
        httpTransport = GoogleNetHttpTransport.newTrustedTransport

        // initialize the data store factory
        dataStoreFactory = new FileDataStoreFactory(DATA_STORE_DIR)

        // authorization
        val credential: Credential = authorize

        // set up global Calendar instance
        client = new com.google.api.services.calendar.Calendar.Builder(httpTransport, JSON_FACTORY, credential).setApplicationName(APPLICATION_NAME).build

     //   client.calendars().delete(calendarId).execute()
        Redirect(routes.ProgramBuilder.index()).flashing("success" -> ("Deleted calendar " + calendarId))
  }

  def newEvent(): Event = {
    val event = new Event()
    event.setSummary("New Event " + RandomStringUtils.randomAlphabetic(4))
    val startDate = new Date()
    val endDate = new Date(startDate.getTime() + 3600000)
    val start = new DateTime(startDate, TimeZone.getTimeZone("UTC"))
    event.setStart(new EventDateTime().setDateTime(start))
    val end = new DateTime(endDate, TimeZone.getTimeZone("UTC"))
    event.setEnd(new EventDateTime().setDateTime(end))

    event.setColorId("4")
    event.setDescription("Une très belle description d'événement")
    event.setSource(new Event.Source().setUrl("http://cfp.devoxx.fr").setTitle("CFP de Devoxx"))
    event.setVisibility("public")
    event.setLocation("La Seine A - Niveau principal")

    event
  }

  /** Authorizes the installed application to access user's protected data. */
  private def authorize: Credential = {
    val inputSecurity = new InputStreamReader(new FileInputStream(new File("app/library/client_secrets.json")))
    val clientSecrets: GoogleClientSecrets = GoogleClientSecrets.load(JSON_FACTORY, inputSecurity)
    if (clientSecrets.getDetails.getClientId.startsWith("Enter") || clientSecrets.getDetails.getClientSecret.startsWith("Enter ")) {
      System.out.println("Enter Client ID and Secret from https://code.google.com/apis/console/?api=calendar " + "into calendar-cmdline-sample/src/main/resources/client_secrets.json")
    }
    val flow: GoogleAuthorizationCodeFlow = new GoogleAuthorizationCodeFlow.Builder(httpTransport, JSON_FACTORY, clientSecrets, Collections.singleton(CalendarScopes.CALENDAR)).setDataStoreFactory(dataStoreFactory).build
    new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver).authorize("user")
  }


  def prepareExport() = IsMemberOf("admin") {
    implicit uuid: String =>
      implicit request =>
        Ok(views.html.ProgramBuilder.showSlot(Slot.conferenceSlotsFriday))
  }
}

