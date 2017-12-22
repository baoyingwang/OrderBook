package baoying.orderbook.testtool;

import java.io.InputStream;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import baoying.orderbook.app.MatchingEngineFIXWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import quickfix.*;

//This is a simplified version.   
//For production code, please read common.DefaultQFJSingleSessionInitiator.
public class FirstQFJClient {

	private final static Logger log = LoggerFactory.getLogger(FirstQFJClientBatch.class);

	public static void main(String[] args) throws Exception {

        String filename = "FIXT11.xml";
        InputStream in = FirstQFJClient.class.getClassLoader().getResourceAsStream(filename);
        if(in == null){
            System.out.println("problem");
            return;
        }

		String configurationFileInClasspath = "testtool/FirstQFJClient.qfj.config.txt";

		Application application = new FirstMessageCallback();

		SessionSettings settings = new SessionSettings(configurationFileInClasspath);
		MessageStoreFactory storeFactory = new FileStoreFactory(settings);
		LogFactory logFactory = new FileLogFactory(settings);
		MessageFactory messageFactory = new DefaultMessageFactory();

		SocketInitiator initiator = new SocketInitiator(application, storeFactory, settings, logFactory,
				messageFactory);

		initiator.start();

		// after start, you have to wait several seconds before sending
		// messages.
		// in production code, you should check the response Logon message.
		// Refer: DefaultQFJSingSessionInitiator.java
		TimeUnit.SECONDS.sleep(3);

		String clientCompID = "LTC$$_FIX_001";
		SessionID sessionID =  new SessionID("FIXT.1.1", clientCompID, MatchingEngineFIXWrapper.serverCompID, "");
		if(! Session.doesSessionExist(sessionID)){
			log.warn("ignore the realtime ER to client, since he:{} is not online now", clientCompID);
			return;
		}

		long period =1;
		TimeUnit unit = TimeUnit.SECONDS;
		ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        Runnable command = new Runnable() {
            @Override
            public void run(){
                try {
                	Session.sendToTarget(FIXOrderBuilder.buildHarcodedNewOrderSingleForTest(), sessionID);
                }catch (Exception e){
                    //TODO log exception
                    e.printStackTrace();
                }

            }
        };
        long initialDelay = 0;
        executor.scheduleAtFixedRate( command,  initialDelay,   period,   unit);

        System.out.println("schedule up");

	}


}
