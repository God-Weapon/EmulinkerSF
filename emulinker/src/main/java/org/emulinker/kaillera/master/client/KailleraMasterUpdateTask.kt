package org.emulinker.kaillera.master.client

import com.google.common.flogger.FluentLogger
import org.apache.commons.httpclient.HttpClient
import org.apache.commons.httpclient.HttpMethod
import org.apache.commons.httpclient.HttpStatus
import org.apache.commons.httpclient.NameValuePair
import org.apache.commons.httpclient.methods.GetMethod
import org.emulinker.kaillera.controller.connectcontroller.ConnectController
import org.emulinker.kaillera.master.PublicServerInformation
import org.emulinker.kaillera.master.StatsCollector
import org.emulinker.kaillera.model.KailleraGame
import org.emulinker.kaillera.model.KailleraServer
import org.emulinker.kaillera.release.ReleaseInfo

private val logger = FluentLogger.forEnclosingClass()

class KailleraMasterUpdateTask(
    private val publicInfo: PublicServerInformation,
    private val connectController: ConnectController,
    private val kailleraServer: KailleraServer,
    private val statsCollector: StatsCollector,
    private val releaseInfo: ReleaseInfo
) : MasterListUpdateTask {

  private val httpClient: HttpClient = HttpClient()

  override fun touchMaster() {
    val createdGamesList = statsCollector.getStartedGamesList()

    val createdGames = StringBuilder()
    synchronized(createdGamesList) {
      val iter = createdGamesList.iterator()
      while (iter.hasNext()) {
        createdGames.append(iter.next())
        createdGames.append("|")
      }
      createdGamesList.clear()
    }
    val waitingGames = StringBuilder()
    for (game in kailleraServer.games) {
      if (game.status != KailleraGame.STATUS_WAITING.toInt()) continue
      waitingGames.append(
          "${game.id}|${game.romName}|${game.owner.name}|${game.owner.clientType}|${game.numPlayers}|")
    }
    val params = arrayOfNulls<NameValuePair>(9)
    params[0] = NameValuePair("servername", publicInfo.serverName)
    params[1] = NameValuePair("port", connectController.bindPort.toString())
    params[2] = NameValuePair("nbusers", kailleraServer.numUsers.toString())
    params[3] = NameValuePair("maxconn", kailleraServer.maxUsers.toString())
    params[4] = NameValuePair("version", "ESF" + releaseInfo.versionString)
    params[5] = NameValuePair("nbgames", kailleraServer.numGames.toString())
    params[6] = NameValuePair("location", publicInfo.location)
    params[7] = NameValuePair("ip", publicInfo.connectAddress)
    params[8] = NameValuePair("url", publicInfo.website)
    val kailleraTouch: HttpMethod = GetMethod("http://www.kaillera.com/touch_server.php")
    kailleraTouch.setQueryString(params)
    kailleraTouch.setRequestHeader("Kaillera-games", createdGames.toString())
    kailleraTouch.setRequestHeader("Kaillera-wgames", waitingGames.toString())
    try {
      val statusCode = httpClient.executeMethod(kailleraTouch)
      if (statusCode != HttpStatus.SC_OK)
          logger.atSevere().log("Failed to touch Kaillera Master: " + kailleraTouch.statusLine)
      else logger.atInfo().log("Touching Kaillera Master done")
    } catch (e: Exception) {
      logger.atSevere().withCause(e).log("Failed to touch Kaillera Master")
    } finally {
      if (kailleraTouch != null) {
        try {
          kailleraTouch.releaseConnection()
        } catch (e: Exception) {}
      }
    }
  }

  init {
    httpClient.setConnectionTimeout(5000)
    httpClient.setTimeout(5000)
  }
}