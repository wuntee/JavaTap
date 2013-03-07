package com.wuntee.ct;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.ArrayReference;
import com.sun.jdi.ByteValue;
import com.sun.jdi.ClassNotLoadedException;
import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.StackFrame;
import com.sun.jdi.VMDisconnectedException;
import com.sun.jdi.Value;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.IllegalConnectorArgumentsException;
import com.sun.jdi.connect.LaunchingConnector;
import com.sun.jdi.connect.VMStartException;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.EventQueue;
import com.sun.jdi.event.EventSet;
import com.sun.jdi.event.MethodEntryEvent;
import com.sun.jdi.event.MethodExitEvent;
import com.sun.jdi.request.EventRequest;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.MethodEntryRequest;
import com.sun.jdi.request.MethodExitRequest;
import com.wuntee.ct.jpda.JpdaWorkshop;
import com.sun.tools.jdi.*;

@SuppressWarnings("restriction")
public class JavaTap {
	public static void main(String[] args) throws IOException, IllegalConnectorArgumentsException, VMStartException, InterruptedException, IncompatibleThreadStateException, AbsentInformationException, ClassNotLoadedException{
		if(args.length == 0){
			usage();
			return;
		}
		JavaTap ct = new JavaTap();
		for(int i=0; i<args.length; i++){
			if(args[i].equalsIgnoreCase("-c") || args[i].equalsIgnoreCase("--config")){
				ct.processConfig(args[++i]);
			}else if(args[i].equalsIgnoreCase("-l") || args[i].equalsIgnoreCase("--launch")){
				String command = args[++i];
				ct.setTapType(JavaTap.TapType.LAUNCH);
				ct.setMainArgs(command);
			}else if(args[i].equalsIgnoreCase("-r") || args[i].equalsIgnoreCase("--remote")){
				String[] hostPort = args[++i].split(":");
				ct.setTapType(JavaTap.TapType.REMOTE);
				ct.setHostname(hostPort[0]);
				ct.setPort(new Integer(hostPort[1]).intValue());
			}else if(args[i].equalsIgnoreCase("-p") || args[i].equalsIgnoreCase("--process")){
				ct.setTapType(JavaTap.TapType.PROCESS);
				ct.setPid(new Integer(args[++i]).intValue());
			}else if(args[i].equalsIgnoreCase("-ls") || args[i].equalsIgnoreCase("--ls")){
				ct.list = true;
			}else{
				System.err.println("Invalid arugment: " + args[i]);
				usage();
				return;
			}
		}
		ct.run();
	}
	
	public static void usage(){
		System.out.println("JavaTap (-c|--config) filename (-l|--launch) javaArgs (-r|--remote) hostname:port (-p|--pid) pid (-ls|--ls)");
		System.out.println("\t-c|--config filename: The configuration file that contains the methods where " +
						   "\n\t\tbreakpoints should be set.");
		System.out.println("\t-l|--launch javaArgs: The full java arugment string as if you were to run a " +
						   "\n\t\tcommand via 'java ...'");
		System.out.println("\t-r|--remote hostname:port: The hostname and port of the remote java process");
		System.out.println("\t-p|--pid pid: Attach to a java VM process. In order for this to work, the process." +
						   "\n\t\tmust be started with the '-agentlib:jdwp=transport=dt_socket,server=y' arguments. " +
						   "\n\t\tThe PID of the java process will then be what is passed as the argument to --pid.");
		System.out.println("\t-ls|--ls: Flag that will cause the applicaiton to list all available classes and exit.");
	}
		
	private String[] entryBreakpoints;
	private String[] exitBreakpoints;
	private JavaTap.TapType type;
	private String mainArgs;
	private int port;
	private int pid;
	private String hostname;
	private boolean list = false;
	private VirtualMachine vm;
	
    void redirectOutput() {
        Process process = vm.process();
        Thread errThread = new StreamRedirectThread(process.getErrorStream(), System.err);
        Thread outThread = new StreamRedirectThread(process.getInputStream(), System.out);
        errThread.start();
        outThread.start();
    }
	
    public enum TapType {
    	LAUNCH, PROCESS, REMOTE
    }
    
    public JavaTap(){
    	
    }
    
    public void setPid(int pid){
    	this.pid = pid;
    }
    
    public void setHostname(String hostname){
    	this.hostname = hostname;
    }
    
    public void setPort(int port){
    	this.port = port;
    }
    
    public void setList(boolean list){
    	this.list = list;
    }
    
    public void setTapType(JavaTap.TapType type){
    	this.type = type;
    }
    
    public void setMainArgs(String mainArgs){
    	this.mainArgs = mainArgs;
    }
    
    public void processConfig(String filename) throws IOException{
    	FileInputStream fstream = new FileInputStream(filename);
    	DataInputStream in = new DataInputStream(fstream);
    	BufferedReader br = new BufferedReader(new InputStreamReader(in));
    	String line = "";
    	
    	List<String> entry = new LinkedList<String>();
    	List<String> exit = new LinkedList<String>();
    	while((line = br.readLine()) != null){
    		if(!line.startsWith("#")){
	    		String[] l = line.split("\\s+");
	    		if(l[0].equalsIgnoreCase("entry")){
	    			entry.add(l[1]);
	    		} else if(l[0].equalsIgnoreCase("exit")) {
	    			exit.add(l[1]);
	    		} else {
	    			System.err.println("Could not process line: " + line);
	    		}
    			entryBreakpoints = entry.toArray(new String[entry.size()]);
    			exitBreakpoints = exit.toArray(new String[exit.size()]);
    		}
    	}
    }
    
    public void run() throws IOException, IllegalConnectorArgumentsException, VMStartException, InterruptedException, IncompatibleThreadStateException, ClassNotLoadedException{
    	if(entryBreakpoints == null || exitBreakpoints == null){
    		System.err.println("Please specify a configuration file.");
    		System.exit(-1);
    	}
    	
    	if(type == JavaTap.TapType.LAUNCH){
			LaunchingConnector connector = JpdaWorkshop.getCommandLineLaunchConnector();
			Map<String, Connector.Argument> arguments = JpdaWorkshop.getMainArgumentsForCommandLineLaunchConnector(connector, mainArgs);
			vm = connector.launch(arguments);
			redirectOutput();			
    	} else if(type == JavaTap.TapType.REMOTE){
    		SocketAttachingConnector connector = JpdaWorkshop.getSocketConnector();
    		Map<String, Connector.Argument> arguments = JpdaWorkshop.getArgumentsForSocketConnector(connector, hostname, port);
    		vm = connector.attach(arguments);
    	} else if(type == JavaTap.TapType.PROCESS){
    		ProcessAttachingConnector connector = JpdaWorkshop.getProccessAttachConnector();
    		Map<String, Connector.Argument> arguments = JpdaWorkshop.getArgumentsForProcessAttachConnector(connector, pid);
    		vm = connector.attach(arguments);
    	}
    	
    	if(list){
    		for(ReferenceType rt : vm.allClasses()){
    			System.out.println(rt.name());
    		}
    		vm.dispose();
    		System.exit(0);
    	} else {
			EventRequestManager mgr = vm.eventRequestManager();		
			addExitBreakpoints(mgr);
			addEntryBreakpoints(mgr);
		
			EventQueue q = vm.eventQueue();
			boolean running = true;
			while(running){
				try{
					EventSet es = q.remove();
					Iterator<Event> it = es.iterator();
					while(it.hasNext()){
						Event e = it.next();
						if(e instanceof MethodEntryEvent){
							processEventEntryBreakpoint((MethodEntryEvent)e);
						} else if(e instanceof MethodExitEvent){
							processEventExitBreakpoint((MethodExitEvent)e);
						}
						es.resume();
					}
				} catch (VMDisconnectedException e) {
					// Application has closed, or the debugger has been disconnected
					System.out.println("The debugger has been disconnected.");
					running = false;
				}
			}
    	}
    }
    
	
	private void processEventExitBreakpoint(MethodExitEvent mee) throws IncompatibleThreadStateException{
		if(shouldBreak(exitBreakpoints, mee)){
			System.out.println(Color.RED + "Exit: " + Color.BOLD_OFF + mee.method() + Color.RESET);
			if(mee.returnValue() instanceof ArrayReference){
				System.out.println(" -ret: " + JpdaWorkshop.arrayReferenceToString((ArrayReference)mee.returnValue()));
			} else {
				System.out.println(" -ret: " + mee.returnValue());
			}
		}		
	}
	
	private void processEventEntryBreakpoint(MethodEntryEvent mee) throws IncompatibleThreadStateException, ClassNotLoadedException, InterruptedException{
		
		if(shouldBreak(entryBreakpoints, mee)){
			System.out.println(Color.BLUE  + "Entry: " + Color.BOLD_OFF + mee.method() + Color.RESET);
			try{
				if(mee.thread().frameCount() > 0){
				
					StackFrame sf = mee.thread().frame(0);
					for(int i=0; i<sf.getArgumentValues().size(); i++){
						Value v = sf.getArgumentValues().get(i);
						String value = "";
						if(v instanceof ArrayReference){
							value = JpdaWorkshop.arrayReferenceToString((ArrayReference)v);
							//System.out.println(" -" + new String(arrayReferenceToByteArray((ArrayReference)v)));
						} else {
							if(v != null){
								value = v.toString();
							}
						}
						System.out.println(" -arg[" + i + "]: " + value);
					}
				}
			} catch(IncompatibleThreadStateException e){
				e.printStackTrace();
			}
		}
	}
	
	private boolean shouldBreak(String[] breakMethods, MethodEntryEvent mee){
		String methodSig = mee.method().declaringType().name() + "." + mee.method().name();
		for(String b : breakMethods){
			if(methodSig.matches("^" + b + ".*")){
				return(true);
			}
		}
		return(false);
	}
	
	private boolean shouldBreak(String[] breakMethods, MethodExitEvent mee){
		String methodSig = mee.method().declaringType().name() + "." + mee.method().name();
		for(String b : breakMethods){
			if(methodSig.matches("^" + b + ".*")){
				return(true);
			}
		}
		return(false);
	}
	
	private void addExitBreakpoints(EventRequestManager mgr){
		List<String> bps = new LinkedList<String>();
		for(String bp : exitBreakpoints){
			String actualBp = bp.substring(0, bp.lastIndexOf('.'));
			if(!bps.contains(actualBp)){
				bps.add(actualBp);
			}
		}
		
		for(String bp: bps){
			MethodExitRequest req = mgr.createMethodExitRequest();
			req.setSuspendPolicy(EventRequest.SUSPEND_ALL);
			req.addClassFilter(bp);
			req.enable();	
		}
	}
	
	private void addEntryBreakpoints(EventRequestManager mgr){
		List<String> bps = new LinkedList<String>();
		for(String bp : entryBreakpoints){
			String actualBp = bp.substring(0, bp.lastIndexOf('.'));
			if(!bps.contains(actualBp)){
				bps.add(actualBp);
			}
		}
		
		for(String bp : bps){
			MethodEntryRequest req = mgr.createMethodEntryRequest();
			req.setSuspendPolicy(EventRequest.SUSPEND_ALL);
			req.addClassFilter(bp);
			req.enable();
		}
	}
		
}

class StreamRedirectThread extends Thread {

    private final Reader in;
    private final Writer out;
    
    private static final int BUFFER_SIZE = 2048;

    StreamRedirectThread(InputStream in, OutputStream out) {
        super();
        this.in = new InputStreamReader(in);
        this.out = new OutputStreamWriter(out);
        setPriority(Thread.MAX_PRIORITY - 1);
    }

    public void run() {
        try {
            char[] cbuf = new char[BUFFER_SIZE];
            int count;
            while ((count = in.read(cbuf, 0, BUFFER_SIZE)) >= 0) {
                out.write(cbuf, 0, count);
            }
            out.flush();
        } catch(IOException exc) {
            System.err.println("Child I/O Transfer - " + exc);
        }
    }
}

class Color {
	public static final String RESET = "\u001B[0m";
	public static final String BLACK = "\u001B[30m";
	public static final String RED = "\u001B[31m";
	public static final String GREEN = "\u001B[32m";
	public static final String YELLOW = "\u001B[33m";
	public static final String BLUE = "\u001B[34m";
	public static final String PURPLE = "\u001B[35m";
	public static final String CYAN = "\u001B[36m";
	public static final String WHITE = "\u001B[37m";
	
	public static final String BOLD_ON = "\u001B[1m";
	public static final String BOLD_OFF = "\u001B[22m";
}
