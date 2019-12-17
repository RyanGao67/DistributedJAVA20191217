package indoc.dev.com.leader.election;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;

// Target learned zookeeper client library's threading model
// IO thread
// event Thread
// Learned how  connect and handle connection (SyncConnected)
// Disconnection (Disconnected / Expired)
// Learned to debug using log levels and the IDE
public class LeaderElection implements Watcher {
	private static final String ZOOKEEPER_ADDRESS = "localhost:2181";
	private static final int SESSION_TIMEOUT = 3000;
	private ZooKeeper zooKeeper;
	
	private static final String ELECTION_NAMESPACE = "/election";
	private String currentZnodeName;
	private static final String TARGET_ZNODE= "/target_znode";
	
	
//	Application's start code in the main method is executed on the main thread
//	When zookeeper object is created, two additional threads are craeted by zookeeper library
//	(1) Event Thread(handles all the zookeeper client state chage event, 
//	eg, connection and disconnection with the zookeeper server, 
//	also handles custom znode watchers and triggers we subscribe to, 
//	Events are executed on Event thread inorder)
//	(2) IO thread (handles all the network communication with zookeeper servers, 
//	handles zookeeper requests and responses responds to pings session menagement, session timeouts)
	public static void main(String[] args) throws IOException, InterruptedException, KeeperException {
		LeaderElection leaderElection = new LeaderElection();
		// create zookeeper object
		leaderElection.connectToZooKeeper();
		
		leaderElection.watchTargetZnode();
		leaderElection.volunteerForLeadership();
		leaderElection.electLeader();
	
		leaderElection.run();
		leaderElection.close();
		System.out.println("Disconnected from Zookeeper, exiting application");
	}
	
	public void volunteerForLeadership() throws KeeperException, InterruptedException {
		String znodePrefix = ELECTION_NAMESPACE + "/c_";
		// znode Prefix: /election/c_
		// data we want to put inside the znode
		// access control list
		// create mode ephemeral sequential if we disconnect from zookeeper, the znode is going to be deleted
		String znodeFullPath = zooKeeper.create(
				znodePrefix, new byte[] {}, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL);
		System.out.println("znode name "+znodeFullPath);
		this.currentZnodeName =znodeFullPath.replace(ELECTION_NAMESPACE+"/", "");
	}
	
	public void electLeader() throws KeeperException, InterruptedException {
		Stat predecessorStat = null;
		String predecessorZnodeName = "";
		while(predecessorStat==null) {
			List<String> children = zooKeeper.getChildren(ELECTION_NAMESPACE, false);
			Collections.sort(children);
			String smallestChild = children.get(0);
			if(smallestChild.contentEquals(currentZnodeName)) {
				System.out.println("I am the leader");
				return;
			}else {
				System.out.println("I am not the leader");
				int predecessorIndex = Collections.binarySearch(children, currentZnodeName)-1;
				predecessorZnodeName = children.get(predecessorIndex);
				predecessorStat = zooKeeper.exists(ELECTION_NAMESPACE + "/" + predecessorZnodeName, this);
			}
		}

		System.out.println("Watching znode"+predecessorZnodeName);
		System.out.println();
	}
	
	// Zookeeper client side api is asynchronise and event driven
	// To get notified about event such as successful connection and disconnection
	// we need to register a event handler, and pass it to zookeeper as a watcher object
	public void connectToZooKeeper() throws IOException {
		this.zooKeeper = new ZooKeeper(LeaderElection.ZOOKEEPER_ADDRESS, SESSION_TIMEOUT, this);
	}
	
	// Without run method: 
	// The event from zookeeper come from a different thread
	// Before zookeeper even has a chance to respond to our application and trigger a event on another thread
	// This application simply finishes
	public void run() throws InterruptedException{
		synchronized(zooKeeper) {
			zooKeeper.wait();
		}
	}
	
	public void close() throws InterruptedException{
		synchronized(zooKeeper) {
			zooKeeper.close();
		}
	}
	
	public void watchTargetZnode() throws KeeperException, InterruptedException {
		Stat stat = zooKeeper.exists(TARGET_ZNODE, this);
		if(stat == null) {return;}
		byte[] data = zooKeeper.getData(TARGET_ZNODE, this, stat);
		List<String> children = zooKeeper.getChildren(TARGET_ZNODE, this);
		System.out.println("Data: "+new String(data) + " children : "+ children);
	}
	
	// This process method will be called by zookeeper lib on a seperate thread 
	// whenever there 's a new event coming from zookeeper server
	public void process(WatchedEvent event) {
		// TODO Auto-generated method stub
		switch(event.getType()) {
			// Zookeeper connection event has no type
			case None:
				if(event.getState() == Event.KeeperState.SyncConnected) {
					System.out.println("Successfully connected to zookeeper");
				}else {
					synchronized(zooKeeper){
						System.out.println("Disconnected from Zookeeper event");
						zooKeeper.notifyAll();
					}
				}
				break;
			case NodeDeleted:
				System.out.println(TARGET_ZNODE + " was deleted ");
				try {
					electLeader();
				} catch (KeeperException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				} catch (InterruptedException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
				break;
			case NodeCreated:
				System.out.println(TARGET_ZNODE + " was created");
				break;
			case NodeDataChanged:
				System.out.println(TARGET_ZNODE + " data changed");
			case NodeChildrenChanged:
				System.out.println(TARGET_ZNODE + " children changed");
				break;
	
		}
		try {
			watchTargetZnode();
		} catch (KeeperException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}


}
