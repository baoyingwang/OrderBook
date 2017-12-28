package baoying.orderbook.testtool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import quickfix.Application;
import quickfix.DoNotSend;
import quickfix.FieldNotFound;
import quickfix.IncorrectDataFormat;
import quickfix.IncorrectTagValue;
import quickfix.Message;
import quickfix.RejectLogon;
import quickfix.SessionID;
import quickfix.UnsupportedMessageType;

/**
 * 
		// "from" means from remote endpoint
		// "to" means to remote endpoint *
 */
public class FirstMessageCallback implements Application {

	private final static Logger log = LoggerFactory.getLogger(FirstMessageCallback.class);
	@Override
	public void fromAdmin(Message paramMessage, SessionID paramSessionID){
		log.debug("fromAdmin session received :  {}" , paramMessage);

	}

	@Override
	public void fromApp(Message paramMessage, SessionID paramSessionID) {
		log.debug("fromApp session received : {} " , paramMessage);

	}

	@Override
	public void onCreate(SessionID paramSessionID) {

		log.debug("onCreate session : {} " , paramSessionID);
	}

	@Override
	public void onLogon(SessionID paramSessionID) {
		log.debug("onLogon session : {} ", paramSessionID);

	}

	@Override
	public void onLogout(SessionID paramSessionID) {
		log.info("onLogout session : {} ", paramSessionID);

	}

	@Override
	public void toAdmin(Message paramMessage, SessionID paramSessionID) {
		log.debug("toAdmin session send : {} ", paramMessage);

	}

	@Override
	public void toApp(Message paramMessage, SessionID paramSessionID) {
		log.debug("toApp session send : {} ", paramMessage);

	}

}
