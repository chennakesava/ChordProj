package service

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.Duration
import akka.actor._

class ChordImpl(val system_name:String="RemoteSystem",val actor_name:String="Master",val port:Int = 2015){
  //retreive the internet ip address
  private var net_ip:String = null
  private val system = ActorSystem(system_name)
  //e.g: "akka.tcp://RemoteSystem@192.168.1.14:2015/user/server"


  val interval = Duration(100, TimeUnit.SECONDS)
  val interval1 = Duration(15, TimeUnit.SECONDS)
//  implicit val executor = system.dispatcher
//  system.scheduler.schedule(Duration(55, TimeUnit.SECONDS), interval, new Runnable{def run(){loadBalance()}})
//  system.scheduler.schedule(Duration(50, TimeUnit.SECONDS), interval, new Runnable{def run(){stabilize()}})
//  system.scheduler.schedule(Duration(120, TimeUnit.SECONDS), Duration(100, TimeUnit.SECONDS), new Runnable{def run(){fix_fingers()}})
//  system.scheduler.schedule(Duration(40, TimeUnit.SECONDS), interval1, new Runnable{def run(){checkPredecessor()}})
//  system.scheduler.schedule(Duration(40, TimeUnit.SECONDS), interval1, new Runnable{def run(){checkSuccessor()}})
//  system.scheduler.schedule(Duration(80, TimeUnit.SECONDS), interval, new Runnable{def run(){examineReferences()}})
 // private val master =system.actorOf(Props(classOf[Chord_Actor]),name = actor_name)

}