import java.rmi.*;
import java.rmi.server.*;
import java.util.*;

public class ChatRemote extends UnicastRemoteObject implements Chat{

    int counter=0, seq=0;
    int last_del = -1;
    String ip_addr="";
    String myId = "";
    HashMap<String, String> id_ip_map=new HashMap<String, String>();
	
    PriorityQueue<MsgItem> msg_queue=new PriorityQueue<MsgItem>(10, new Comparator<MsgItem>() { 
	    public int compare(MsgItem m1, MsgItem m2) {
		if(m1.seq<m2.seq) {
		    return 0;
		}
		return 1;
	    }
	});
	
    ChatRemote()throws RemoteException{
	super();
    }
	
    public void setIdIp(String id, String ip)
    {
	myId = id;
	ip_addr=ip;
    }
	
    public void insertIdIp(String id, String ip) {
	id_ip_map.put(id, ip);
	Chat stub;
	try {
	    stub=(Chat)Naming.lookup("rmi://" + ip + ":5000/" + id);
	    stub.notifyIdIp(myId, ip_addr);
	} catch (Exception e){
	    System.out.println (e);
	}
    }

    public void notifyIdIp (String id, String ip) {
       	id_ip_map.put(id, ip);
    }
    
    //Adds the message (with status as undeliverable) to the queue and suggests a sequence number
    public int return_seq(String id, String ip, String msg, int m_id, int status) {
	seq++;
	//Add the message in the queue
	//	- Status: Undeliverable (0)
	// 	- id: m_id
	//	- sender: ip
	//	- msg: msg
	//	- suggested_seq: seq
	//	- replyTo flag: replyToFlag
	//	- replyTo Client ip: replyToClientIP
	MsgItem queue_item=new MsgItem();
	queue_item.status=status;
	queue_item.msg_id=m_id;
	queue_item.sender_id=id;	
	queue_item.sender_ip=ip;
	queue_item.msg=msg;
	queue_item.seq=seq;

	msg_queue.add(queue_item);
		
		
	//Sending back the suggested seq_number
	return seq;
    }

    //Adds the message (with status as undeliverable) to the queue and suggests a sequence number
    public void enqueue_self_message(String id, String ip, String msg, int m_id, int seq, int status) {
	//Add the message in the queue
	//	- Status: Undeliverable (0)
	// 	- id: m_id
	//	- sender: ip
	//	- msg: msg
	//	- suggested_seq: seq
	//	- replyTo flag: replyToFlag
	//	- replyTo Client ip: replyToClientIP
	MsgItem queue_item=new MsgItem();
	queue_item.status=status;
	queue_item.msg_id=m_id;
	queue_item.sender_id=id;	
	queue_item.sender_ip=ip;
	queue_item.msg=msg;
	queue_item.seq=seq;

	msg_queue.add(queue_item);
		
    }

    void print () {
	System.out.println ("Printing");
	Iterator it=msg_queue.iterator();
	while(it.hasNext()) {
	    MsgItem tmp1=(MsgItem)it.next();
	    System.out.println (tmp1.seq);
	}
	System.out.println ("Done Printing");
    }
    
    public void set_deliver(String id, String ip_addr, int m_id, int max_seq, int status) {
	//Update the sequence number
	seq=max_seq;
		
	//Changes in the queue
	//	- Update the sequence number
	//	- Change status from undeliverable to deliverable (if replyTo then check for causal)
	Iterator it=msg_queue.iterator();
	String tmp="";
	MsgItem tmp1=new MsgItem();;
	while(it.hasNext())
	    {
		tmp1=(MsgItem)it.next();
		if(tmp1.msg_id==m_id && (tmp1.sender_id.equals (id)))
		    {
			tmp=tmp1.msg;
			break;
		    }
	    }
	msg_queue.remove(tmp1);
		
	MsgItem queue_item=new MsgItem();
	queue_item.status=status;
	queue_item.msg_id=m_id;
	queue_item.sender_id = id;
	queue_item.sender_ip=ip_addr;
	queue_item.msg=tmp;
	queue_item.seq=max_seq;

	msg_queue.add(queue_item);
	
	//Check the front of the queue to display a message
	MsgItem head=msg_queue.peek();

	if (last_del == -1) {
	    last_del = head.seq - 1;
	}
	
	while(head.status!=0 && (head.seq == last_del + 1)) {
	    //System.out.println("Head queue is 1");
	    msg_queue.poll();
	    last_del++;

	    if (head.status == -1) {
		if (head.sender_id == myId) {
		    System.exit (0);
		}
		else {
		    id_ip_map.remove (head.sender_id);
		}
	    }
	    
	    if(id_ip_map.containsKey(head.sender_id))
		System.out.println("#" + head.sender_id + ": " + head.msg);
	    if(!msg_queue.isEmpty())
		head=msg_queue.peek();
	    else
		break;

	}
	
    }

    
    
    public void reply(String msg) {
	seq++;
	int max_seq=0, l_seq;
	counter++;
	Chat stub;
		
	try{
	    //Call return_seq on each node using their ip
	    for(String id:id_ip_map.keySet())
		{
		    stub=(Chat)Naming.lookup("rmi://"+id_ip_map.get(id)+":5000/" + id);
		    l_seq=stub.return_seq(myId, ip_addr, msg, counter, 0);
		    if(max_seq<l_seq)	max_seq=l_seq;
		}
	    if (max_seq < seq) max_seq = seq;
			
	    //Inform each node of the chosen seq
	    for(String id:id_ip_map.keySet())
		{
		    stub=(Chat)Naming.lookup("rmi://"+id_ip_map.get (id)+":5000/" + id);
		    stub.set_deliver(myId, ip_addr, counter, max_seq, 1);
		}
	    enqueue_self_message (myId, ip_addr, msg, counter, max_seq, 1);	    
	}
	catch(Exception e){System.out.println(e);}
    }
	
    //Send leave orders to every other node
    public void leave() {
	seq++;
	int max_seq=0, l_seq;
	counter++;
	Chat stub;

	try{
	    for(String id:id_ip_map.keySet())
		{
		    stub=(Chat)Naming.lookup("rmi://"+id_ip_map.get (id)+":5000/" + id);
		    l_seq = stub.return_seq (myId, ip_addr, "", counter, -1);
		    if(max_seq<l_seq)	max_seq=l_seq;
		}
	    if (max_seq < seq) max_seq = seq;
	}
	catch(Exception e){System.out.println(e);}

	try {
	    //Inform each node of the chosen seq
	    for(String id_iter:id_ip_map.keySet())
		{
		    stub=(Chat)Naming.lookup("rmi://"+id_ip_map.get(id_iter)+":5000/" + id_iter);
		    stub.set_deliver(myId, ip_addr, counter, max_seq, -1);
		}
	} catch (Exception e) {System.out.println(e);}
	enqueue_self_message (myId, ip_addr, "", counter, max_seq, -1);

	//Check the front of the queue to display a message
	MsgItem head=msg_queue.peek();

	if (last_del == -1) {
	    last_del = head.seq - 1;
	}
	
	while(head.status!=0 && (head.seq == last_del + 1)) {
	    msg_queue.poll();
	    last_del++;

	    if (head.status == -1) {
		if (head.sender_id == myId) {
		    System.exit (0);
		}
		else {
		    id_ip_map.remove (head.sender_id);
		}
	    }
	    
	    if(id_ip_map.containsKey(head.sender_id))
		System.out.println("#" + head.sender_id + ": " + head.msg);
	    if(!msg_queue.isEmpty())
		head=msg_queue.peek();
	    else
		break;

	}
	
    }

    public void replyTo(String id, String msg)
    {
	seq++;
	int max_seq=0, l_seq;
	counter++;
	Chat stub;
		
	try{
	    //Call return_seq on each node using their ip
	    for(String id_iter:id_ip_map.keySet())
		{
		    stub=(Chat)Naming.lookup("rmi://"+id_ip_map.get(id_iter)+":5000/" + id_iter);
		    l_seq=stub.return_seq(myId, ip_addr, msg, counter, 0);
		    if(max_seq<l_seq)	max_seq=l_seq;
		}
	    if (max_seq < seq) max_seq = seq;
			
	    //Inform each node of the chosen seq
	    for(String id_iter:id_ip_map.keySet())
		{
		    stub=(Chat)Naming.lookup("rmi://"+id_ip_map.get(id_iter)+":5000/" + id_iter);
		    stub.set_deliver(myId, ip_addr, counter, max_seq, 1);
		}
	    enqueue_self_message (myId, ip_addr, msg, counter, max_seq, 1);
	}
	catch(Exception e){System.out.println(e);}
    }

}
