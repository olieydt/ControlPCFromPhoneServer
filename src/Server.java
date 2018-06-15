import java.awt.AWTException;
import java.awt.Dimension;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Enumeration;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
public class Server {

	private static Robot mouseController;
	private static ServerSocket serverSocket;
	private static MulticastSocket multiSocket;
	//private static InetAddress multiGroup;
	private static SocketAddress multiGroup;
	private static Socket mouseSocket;
	private static NetworkInterface networkInterface;
	public static void main(String[] args) throws IOException, InterruptedException{
		//Add shutdown hook
		Runtime.getRuntime().addShutdownHook(new Thread()
		{
		    @Override
		    public void run()
		    {
		        closeAllSockets();
		    }
		});
		
		try {
			//Get client address, start multicast
			startConnectionSequence();
		} catch (AWTException e) {
			e.printStackTrace();
		} catch(IOException e) {
			e.printStackTrace();
		}
	}
	
	private static void closeAllSockets() {
		if(serverSocket != null && !serverSocket.isClosed()) {
	    		try {
				serverSocket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	    if(multiSocket != null && !multiSocket.isClosed()) {
	    		try {
				multiSocket.leaveGroup(multiGroup, networkInterface);
				multiSocket.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	    }
	    if(mouseSocket != null && !mouseSocket.isClosed()) {
	    		try {
				mouseSocket.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	    }
	}

	private static void sendLocalAddress(InetAddress clientAddress) throws IOException {
		DatagramSocket socket = new DatagramSocket();
		socket.setSoTimeout(5000);
		DatagramPacket sendPacket = new DatagramPacket("PCIP".getBytes(), "PCIP".getBytes().length, clientAddress, 12346);
		socket.send(sendPacket);
		socket.close();
	}

	private static void startController(InetAddress client) throws AWTException, IOException {
		mouseController = new Robot();
		serverSocket = new ServerSocket(12347);
		serverSocket.setSoTimeout(10000);
		while(true) {
			try {
				mouseSocket = serverSocket.accept();
				//System.out.println("Connected");
				DataInputStream input;
				byte[] received = new byte[1024];
				input = new DataInputStream(mouseSocket.getInputStream());
			    int lenBytes = input.read(received);
			    controlMouse(new String(received));
			    //System.out.println(new String(received));
				mouseSocket.close();
			} catch (SocketTimeoutException e) {
				
			} catch (IOException e) {
				
			}
		}
	}
	
	private static String getUsedNetworkInterfaceName() throws IOException {
		String cmd[] = new String[3];
		if(System.getProperty("os.name").toLowerCase().equals("Mac OS X".toLowerCase())) {
			cmd[0] = "/bin/sh";
			cmd[1] = "-c";
			cmd[2] = "route -n get default | grep 'interface:' | grep -o '[^ ]*$'";	
		} else {
			cmd[0] = "cmd.exe";
			cmd[1] = "/c";
			cmd[2] = "route | grep '^default' | grep -o '[^ ]*$'";
		}
		Process p = Runtime.getRuntime().exec(cmd);
		BufferedReader stdInput = new BufferedReader(new 
			     InputStreamReader(p.getInputStream()));
		String output = stdInput.readLine();
		return output;
	}

	private static InetAddress findClient() throws IOException {
		multiSocket = new MulticastSocket(12345);
		//multiGroup = InetAddress.getByName("230.0.0.0");
		multiGroup = new InetSocketAddress("230.0.0.0", 12345);
		//multiSocket.joinGroup(multiGroup);
		String interfaceName = getUsedNetworkInterfaceName();
		networkInterface = NetworkInterface.getByName(interfaceName);
		multiSocket.joinGroup(multiGroup, networkInterface);
		byte[] buf = new byte[128];
		while(true) {
			DatagramPacket packet = new DatagramPacket(buf, buf.length);
			multiSocket.receive(packet);
			String received = new String(packet.getData(), 0, packet.getLength());
			if(received.contains("DEVICEIP")) {
				multiSocket.leaveGroup(multiGroup, networkInterface);
				//multiSocket.leaveGroup(multiGroup);
				multiSocket.close();
				return packet.getAddress();
			}
		}
		/*DatagramSocket s = new DatagramSocket(12345);
		byte[] buf = new byte[128];
		DatagramPacket packet = new DatagramPacket(buf, buf.length);
		s.receive(packet);
		String received = new String(packet.getData(), 0, packet.getLength());
		System.out.println(received);
		return packet.getAddress()*/
	}

	private static void controlMouse(String mouseCommand) throws IOException, AWTException {
		if(mouseController != null) {
			if(mouseCommand.trim().equals("click")) {
				//apply click
				mouseController.mousePress(InputEvent.BUTTON1_DOWN_MASK);
			    mouseController.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
			} else if(mouseCommand.trim().equals("end")) {
				closeAllSockets();
				startConnectionSequence();
			} else {
				//move mouse by deltaX and deltaY from finger move on phone
				String coordinates[] =  mouseCommand.split(",");
				if(coordinates.length > 0) {
					String startCoord[] = coordinates[0].split(":");
					String endCoord[] = coordinates[coordinates.length-1].split(":");
					Point currentMouseCoord = MouseInfo.getPointerInfo().getLocation();
					Point newMouseCoord = new Point(currentMouseCoord.x + (int)(Float.parseFloat(endCoord[0]) - Float.parseFloat(startCoord[0])),
							currentMouseCoord.y + (int)(Float.parseFloat(endCoord[1]) - Float.parseFloat(startCoord[1])));
					mouseController.mouseMove(newMouseCoord.x, newMouseCoord.y);
				}
			}
		}
	}

	private static void startConnectionSequence() throws IOException, AWTException {
		InetAddress client = findClient();
		sendLocalAddress(client);
		startController(client);
	}

}
