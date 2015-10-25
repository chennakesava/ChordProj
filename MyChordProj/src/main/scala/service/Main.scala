package service

import akka.actor.{ActorSystem,Props}
object Main extends App {
    override def main(args:Array[String]){
      if(args.length != 2) {
        println("Mismatch with num of arguments required, defaulting num_nodes to 10 and requests to 10")
        InitChord(num_nodes = 4,num_requests = 10)
      } else {
        InitChord(args(0).toInt,args(1).toInt)
      }

      def InitChord(num_nodes:Int, num_requests:Int) = {
        val system = ActorSystem("ChordMaster")
        val masterChord = system.actorOf(Props(new ChordMaster(num_nodes,num_requests)),name="master")
        masterChord ! "start"
      }
    }
}