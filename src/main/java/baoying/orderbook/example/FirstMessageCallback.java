package baoying.orderbook.example;

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
	

	@Override
	public void fromAdmin(Message paramMessage, SessionID paramSessionID)
			throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, RejectLogon {
		System.out.println("fromAdmin session received :  " + paramMessage.toString());

	}

	@Override
	public void fromApp(Message paramMessage, SessionID paramSessionID)
			throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, UnsupportedMessageType {
		System.out.println("fromApp session received :  " + paramMessage.toString());

	}

	@Override
	public void onCreate(SessionID paramSessionID) {
		System.out.println("onCreate session :  " + paramSessionID.toString());
	}

	@Override
	public void onLogon(SessionID paramSessionID) {
		System.out.println("onLogon session :  " + paramSessionID.toString());

	}

	@Override
	public void onLogout(SessionID paramSessionID) {
		System.out.println("onLogout session :  " + paramSessionID.toString());

	}

	@Override
	public void toAdmin(Message paramMessage, SessionID paramSessionID) {
		System.out.println("toAdmin session send :  " + paramMessage.toString());

	}

	@Override
	public void toApp(Message paramMessage, SessionID paramSessionID) throws DoNotSend {
		System.out.println("toApp session send :  " + paramMessage.toString());

	}

}
