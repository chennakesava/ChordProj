package service

import akka.actor.{ActorSelection, ActorRef, Props, Actor}

import scala.collection.mutable.ArrayBuffer
import scala.util.Random

class ChordMaster(num_nodes: Int, num_requests: Int) extends Actor{
  //limiting max num_of_entries to 5 and total identities to 2^5 = 32
  val m:Int = 5
  val max_id = 32
  //create chord actors to be spawned with given num__nodes one by one
  val randomIdList = generateSeq()
  val chordId:Int = 0
  val primeChord = context.actorOf(Props(new Chord_Actor(chordId)),name="0")

  def generateSeq(): ArrayBuffer[Int] = {
    var nums: ArrayBuffer[Int] = new ArrayBuffer[Int](max_id)
    for(i <- 1 until max_id)
      nums+= i
    Random.shuffle(nums)
  }

  def receive = {
    case "start" => {
      println(s"Received message to start $num_nodes and $num_requests requests.")
//      val newChord = context.actorOf(Props(new Chord_Actor(11)),name = String.valueOf(11))
//      newChord ! join(context.actorSelection("/user/master/0"))
//      Thread.sleep(1000)
//      val newChord2 = context.actorOf(Props(new Chord_Actor(19)),name = String.valueOf(19))
//      newChord2 ! join(context.actorSelection("/user/master/11"))
      val r = scala.util.Random
      for(i <- 0 until num_nodes) {
        val nodeid = randomIdList(i)
        val newChord = context.actorOf(Props(new Chord_Actor(nodeid)),name = String.valueOf(nodeid))

        if(i > 1) {
          val randomnum = r.nextInt(i-1)
          println(s"****************chord $nodeid is joining chord $randomIdList($randomnum) ******")
          val randomChord = context.actorSelection("/user/master/"+randomIdList(randomnum))
          newChord ! join(randomChord)
        }else
          newChord ! join(context.actorSelection("/user/master/0"))
        Thread.sleep(10000)
      }

//      //for(i <- 1 until num_nodes) {
      val randomnum = r.nextInt(num_nodes)
      Thread.sleep(10000)
      context.actorSelection("/user/master/"+randomIdList(randomnum)) ! printFingerTable()

      Thread.sleep(1000)
      context.actorSelection("/user/master/"+randomIdList(randomnum)) ! printSuccessors()
    }
  }
}
