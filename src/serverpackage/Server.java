package serverpackage;
import java.awt.AWTException;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
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

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
public class Server {

	private static Robot mouseController;
	private static ServerSocket serverSocket;
	private static MulticastSocket multiSocket;
	//private static InetAddress multiGroup;
	private static SocketAddress multiGroup;
	private static Socket mouseSocket;
	private static NetworkInterface networkInterface;
	
	private static Thread sendIpThread = null;
	private volatile static boolean sendIpThreadRunning = true;
	
	//Jframe components
	private static JButton button;
	private static JLabel label;
	
	public static void main(String[] args) throws IOException, InterruptedException{
		//Add shutdown hook
		Runtime.getRuntime().addShutdownHook(new Thread()
		{
		    @Override
		    public void run()
		    {
		        System.out.print("\nShutting down server");
		        resetThread();
		        closeAllSockets();
		        typewriterPrint("...");
		        System.out.println("Good bye.");
		    }
		});
		//make window
		createJFrame();
	}
	
	private static void createJFrame() {
		JFrame frame = new JFrame("LaptorControlServer");
		frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		frame.addWindowListener(new java.awt.event.WindowAdapter() {
		    @Override
		    public void windowClosing(java.awt.event.WindowEvent windowEvent) {
		    	//print in window
		    	System.exit(0);
		    }
		});
		//add buttons
		frame.getContentPane().setLayout(new FlowLayout());
		label = new JLabel("No devices found on network.");
		frame.getContentPane().add(label);
		button = new JButton("Connect");
		button.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				if(button.getText().equals("Connect")) {
					//Get client address, start multicast
					label.setText("Looking for devices...");
					Thread t = new Thread(new Runnable() {
						public void run() {
							try {
								button.setEnabled(false);
								startConnectionSequence();
							} catch (AWTException e) {
							} catch(IOException e) {
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
						}
					});
					t.start();
				} else {
					resetThread();
					closeAllSockets();
				}
			}
		});
		frame.getContentPane().add(button);
		frame.pack();
		frame.setVisible(true);
	}

	private static void typewriterPrint(String strToPrint) {
		try {
			int i;
			for(i=0; i<strToPrint.length()-1; i++) {
				System.out.print(strToPrint.charAt(i));
				Thread.sleep(400);
			}
			System.out.println(strToPrint.charAt(i));
		} catch (InterruptedException e) {
			
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
	    label.setText("No device found.");
	    button.setText("Connect");
	}

	private static void sendLocalAddress(InetAddress clientAddress) throws IOException, InterruptedException {
		DatagramSocket socket = new DatagramSocket();
		socket.setSoTimeout(5000);
		DatagramPacket sendPacket = new DatagramPacket("PCIP".getBytes(), "PCIP".getBytes().length, clientAddress, 12346);
		while(sendIpThreadRunning) {
			socket.send(sendPacket);
			Thread.sleep(300);
		}
		socket.close();
	}

	private static void startController(InetAddress client) throws AWTException, IOException, InterruptedException {
		mouseController = new Robot();
		serverSocket = new ServerSocket(12347);
		serverSocket.setSoTimeout(10000);
		while(true) {
			try {
				mouseSocket = serverSocket.accept();
				//received a command from phone, stop sending ip address
				sendIpThreadRunning = false;
				sendIpThread.join();
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

	private static void controlMouse(String mouseCommand) throws IOException, AWTException, InterruptedException {
		if(mouseController != null) {
			if(mouseCommand.trim().equals("click")) {
				//apply click
				mouseController.mousePress(InputEvent.BUTTON1_DOWN_MASK);
			    mouseController.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
			} else if(mouseCommand.trim().equals("end")) {
				closeAllSockets();
				resetThread();
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

	private static void resetThread() {
		if(sendIpThreadRunning && sendIpThread != null) {
			sendIpThreadRunning = false;
			try {
				sendIpThread.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		sendIpThreadRunning = true;
	}

	private static void startConnectionSequence() throws IOException, AWTException, InterruptedException {
		InetAddress client = findClient();
		sendIpThread = new Thread("Ip Thread") {
			public void run() {
				try {
					sendLocalAddress(client);
				} catch (IOException e) {
					e.printStackTrace();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		};
		sendIpThread.start();
		label.setText("Device found!");
		button.setText("Disconnect");
		button.setEnabled(true);
		startController(client);
	}

}
