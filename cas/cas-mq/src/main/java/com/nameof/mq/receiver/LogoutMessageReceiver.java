package com.nameof.mq.receiver;

import com.nameof.common.utils.HttpRequest;
import com.nameof.mq.message.LogoutMessage;
import com.nameof.mq.message.Message;

/**
 * 注销消息接收者
 * 
 * @author ChengPan
 */
public class LogoutMessageReceiver extends Receiver{
	
	@Override
	public void handleMessage(Message message) {
		if (message != null) {
			LogoutMessage logoutMsg = new LogoutMessage(message);
			if (logoutMsg != null && logoutMsg.getLogoutUrls() != null) {
				for (String logoutUrl : logoutMsg.getLogoutUrls()) {
					logger.debug("{}：{}注销", logoutMsg.getToken(), logoutUrl);
					HttpRequest.sendPost(logoutUrl, "token=" + logoutMsg.getToken(), null);
				}
			}
		}
	}

}
