package service

import java.math.BigInteger
import java.security.MessageDigest

import data._
import akka.actor.{ActorSelection, Actor}
import akka.util.Timeout
import java.util.logging.{Level, Logger}

import sun.security.provider.SHA

case class request(req:Request)
case class join(node: ActorSelection)
case class response(code:String, e:Set[Entry])
case class find_successor(sn:Node,id:ID)
case class successor()
case class request_predecessor()
case class closestPrecedingNode(id:ID)
case class successor_found(s:Node)
case class update_predecessor(pre:Node)
case class update_finger_table(s:Node,i:Int)
case class uploadEntry(e:Entry)
case class uploadEntries(e:Set[Entry])
case class recSuccessorList(sl:List[Node])
case class requestAll()
case class transmitBackupEntries(n:Node, e:List[Set[Entry]])
case class carryEntries(n:Node,e:List[Set[Entry]])
case class printSuccessors()
case class printFingerTable()

class Chord_Actor(val nodeId:Int) extends Actor{

  private val logger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME)
  private val entries = new Entries()
  private val responses = new Responses()
  private val counter:Int = 0

  private val localURL:String = "/user/master/"+nodeId
  private val nodeID = HashFunction.createID(localURL.getBytes)
  private val localNode = new Node(nodeID,localURL)
  private val successorList = new SuccessorList(localNode)
  private val fingerTable = new FingerTable(nodeID, localNode,successorList)
  private val references = new References(localNode,fingerTable,successorList,localNode)
  implicit val timeout = Timeout(50000)

  override def receive: Receive = {
    case join(node) => {
      this.join(node)
    }

    case printFingerTable() => {
      println("Printing finger table..........")
      this.fingerTable.print()
    }
    //once a new node join in a network, it updates the nodes preceding it through this case class
    case update_finger_table(s,i)=>{
      if(!(references.contains(s) && s.equals(this.localNode))|| i < 5){
        logger.info("receive new node["+s.getURL+"]")
        references.addReference(s)
        if(i+1 < 50 && references.getPredecessor != null && 
            !references.getPredecessor.equals(localNode)){
          logger.info("inform predecessor about the new joining node["+s.getURL+"]")
          context.actorSelection(references.getPredecessor.getURL) ! update_finger_table(s,i+1)
        }
      }else{
        logger.info("Node ["+s.getURL+"] already exists")
      }
    }
    
    //request all the node references that a nodes stores, including itself
    case requestAll() =>{
       logger.info("receive node list from ["+sender.path.toString()+"]")
       var nl = references.getSuccessorList.getCopy
       val pre = references.getPredecessor
       if(pre != null)
         nl = nl:+pre
       nl = nl:+localNode
       sender ! nl           
    }
   
    //add successor to references table
    case successor_found(s) => {
      references.addReference(s)
    }
    
    //add successor list to references table one by one
    case recSuccessorList(sl)=>{
      sl.foreach { x => references.addReference(x) }
    }
    
   
    case update_predecessor(pre) =>{
     references.updatePredecessor(pre)
    }

    case find_successor(rn, id)=>{      
      println("find_sucessor")
      logger.info("find successors for ["+rn.getURL+"]")
      if(rn !=null && id != null){
        var c = references.getClosestPrecedingNode(id)
        var s:Node = references.getSuccessor
        if(s == null || s.equals(localNode)){
          if(!rn.equals(localNode)){ 
            logger.info("I am the only node in the network, send myself's info to node["+rn.getURL+"]")
            context.actorSelection(rn.getURL) ! successor_found(localNode)      
            context.actorSelection(rn.getURL) ! update_predecessor(localNode)       
            references.addReference(rn)
           }
        }
        else if(id.isInInterval(localNode.getID, s.getID)){
            logger.info("I am the predecesor for node["+rn.getURL+"]")
            context.actorSelection(rn.getURL) ! successor_found(s)      
            context.actorSelection(rn.getURL) ! update_predecessor(localNode)
            references.addReference(rn)
            var temp = references.getSuccessorList.getCopy
            if(!temp.isEmpty)
              context.actorSelection(rn.getURL) ! recSuccessorList(temp)
        }
        else{
           if(c != null && !c.equals(localNode)){
              context.actorSelection(c.getURL) ! find_successor(rn,id)  
              logger.info("find closest predecessor["+c.getURL+"] for node["+rn.getURL+"],passing task to it")
           }
           else{
              logger.info("happens to be the successor of node["+rn.getURL+"]")
              context.actorSelection(rn.getURL) ! successor_found(localNode)      
              context.actorSelection(rn.getURL) ! successor_found(this.references.getPredecessor)
              var temp = references.getSuccessorList.getCopy
              if(!temp.isEmpty)
                context.actorSelection(rn.getURL) ! recSuccessorList(temp)             
          }
        }   
      }    
    } 
    
    /*
     * Recursively find the successor that is responsible for entry e and upload the entry e to this 
     * corresponding successor  
     */
    case uploadEntry(e:Entry) =>{
      val cp = references.getClosestPrecedingNode(e.getID())
      val succ = references.getSuccessor
      val pre = references.getPredecessor
      if(pre != null && !pre.equals(localNode) && e.getID().isInInterval(pre.getID, localNode.getID)){
        this.entries.add(e)
        logger.info("Entry with ["+e.getKey().toString()+"] is uploaded to the current node")
      }
      else if(cp != null && !cp.equals(localNode)){
        logger.info("find key["+e.getKey()+"]'s closest predecessor, pass the upload task to it")
        context.actorSelection(cp.getURL) ! uploadEntry(e)
      }
      else if(succ != null){
        context.actorSelection(succ.getURL) ! uploadEntry(e)
        logger.info("pass the upload task to successor")
      }
      else{
        this.entries.add(e)  
        logger.info("Entry with ["+e.getKey().toString()+"] is uploaded to the current node")
      }      
    }

    case request(req) =>{
      val key = req.reqKey
      val cp = references.getClosestPrecedingNode(key)
      val succ = references.getSuccessor
      val pre = references.getPredecessor
      if(pre != null && !pre.equals(localNode) && key.isInInterval(pre.getID, localNode.getID) )
        context.actorSelection(req.reqNode.getURL) ! response(req.reqID, entries.getEntries(req.reqKey))  
      else if(succ != null && key.isInInterval(localNode.getID, succ.getID))
        context.actorSelection(succ.getURL) ! request(req)
      else if(cp != null && !cp.equals(localNode))
        context.actorSelection(cp.getURL) ! request(req)
      else if(succ != null)
        context.actorSelection(succ.getURL) ! request(req)
      else
        context.actorSelection(req.reqNode.getURL) ! response(req.reqID, entries.getEntries(req.reqKey))        
    }
    
    //carry entries for node n, which most likely is predecessor of current node
    case carryEntries(n,e) =>{
      var succ = this.references.getSuccessor
      if(succ!=null && !this.localNode.equals(succ) && n.getID.isInInterval(this.localNode.getID, succ.getID))
        context.actorSelection(succ.getURL) ! carryEntries(n,e)
      else{
        logger.info("carry entries for dead predecessor")
        e.foreach { x => this.entries.addAll(x) }
      }
         
    }
       
    case uploadEntries(e) => entries.addAll(e)
    
    case response(id,res) => this.responses.receive(id, res)
        
    case successor() => sender ! references.getSuccessor
    
    case request_predecessor() => sender ! references.getPredecessor
    
    case printSuccessors() => this.printSL()
  }

  def hashIdentifier(identifier: String, identifierBits: Int): BigInteger =  {

    val mDigest = MessageDigest.getInstance("SHA1")
    val sb = new StringBuffer()
    mDigest.reset()
    mDigest.update(identifier.getBytes)
    val digestResult = mDigest.digest()
    for (i <- 0 until 2) {
      sb.append(Integer.toString((digestResult(i) & 0xff) + 0x100, 16).substring(1))
    }
    val bigInteger = new BigInteger(sb.toString(), 16)
    return bigInteger
  }

  def join(node:ActorSelection){
    try{
        logger.info("Start trying to join a network from:["+node+" id]")
        node ! find_successor(localNode,localNode.getID)
        new Thread(new Runnable{def run{update_others()}}).start()
    }
    catch{
      case e:Exception => logger.log(Level.SEVERE, "Exception thrown while trying to join", e)
    }
  }


  /**
   * to initialize finger table, not used in practice
   */
  def init_finger_table(a:ActorSelection){
    for(i <- 0 until nodeID.getLength()){
      Thread.sleep(200)
      try{
        var finger = nodeID.addPowerofTwo(i)
        a ! find_successor(localNode,finger)
      }
      catch{
        case e:Exception => println(e.getMessage)
      }
    }
  }

  /**
   * waiting until the predecessor is retrieved, pass the node to inform the others
   */
  def update_others(){
    logger.info("Joining a network, waiting for predecessor.")
    while(references.getPredecessor == null || references.getPredecessor.equals(localNode)){
      Thread.sleep(10)
    }
    logger.info("Starting informing predecessors.")
    context.actorSelection(references.getPredecessor.getURL) ! update_finger_table(localNode,1)
  }

  /**
   * periodically call this function to update the finger table
   */
  def fix_fingers(){
    val r = new scala.util.Random(System.currentTimeMillis())
    val num = r.nextInt(nodeID.getLength())
    try{
      val start = nodeID.addPowerofTwo(num)
      if(start !=null )
        self ! find_successor(localNode,start)
    }
    catch{
      case e:Exception => logger.log(Level.SEVERE, "", e)
    }
  }

  /**
   * providing a key and lookup the corresponded entry set
   */
  def lookup(key:ID):Set[Entry] = {
    var reqID = this.makeRequestID()
    val req = new Request(this.localNode, reqID, key)
    self ! request(req)
    var t = 0
    while(!responses.containsKey(reqID)){
      if(t > 500)
        return Set()
      t+=1
      Thread.sleep(2)
    }
    responses.remove(reqID)
  }

  private def makeRequestID():String = System.currentTimeMillis().toString() +"_"+counter

  /**
   * find a node that is responsible for an entry and upload the entry to this node
   */
  def upload(toUpload:Entry){
    var entryID = toUpload.getID()
    var cp = references.getClosestPrecedingNode(entryID)
    var pre  = references.getPredecessor
    if(pre != null && entryID.isInInterval(pre.getID, this.localNode.getID)){
      logger.info("Entry with ["+toUpload.getKey().toString()+"] is uploaded to the current node")
      entries.add(toUpload)
    }
    else if(cp != null && !cp.equals(localNode)){
      logger.info("find key["+toUpload.getKey()+"]'s closest predecessor, pass the upload task to it")
      context.actorSelection(cp.getURL) ! uploadEntry(toUpload)
    }
    else if(references.getSuccessor != null){
      logger.info("pass the upload task to successor")
      context.actorSelection(references.getSuccessor.getURL) ! uploadEntry(toUpload)
    }
    else{
      entries.add(toUpload)
      logger.info("Entry with ["+toUpload.getKey().toString()+"] is uploaded to the current node")
    }
  }

  def printS(){
    if(references.getSuccessor != null)
      println(references.getSuccessor.getURL)
  }

  def print_pre(){
    println(references.getPredecessor.getURL)
  }

  def print_entries(){
    var temp = entries.getValues
    temp.foreach { x => x.foreach { y => println("key:"+y.getKey()+", value:"+y.getValue()) }}
  }

  def printSL(){
    println("Printing successorList......")
    this.successorList.getCopy.foreach { x => println(x.getURL) }
  }
}