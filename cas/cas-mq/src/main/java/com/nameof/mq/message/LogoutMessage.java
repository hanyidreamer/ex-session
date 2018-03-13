package com.nameof.mq.message;

import java.util.List;

import com.nameof.common.utils.JsonUtils;

/**
 * 注销消息
 * @author ChengPan
 */
public class LogoutMessage extends Message{

	private static final long serialVersionUID = 1923709448488814904L;
	
	private InnerMessage logoutMessage;
	
	public LogoutMessage() {}
	
	public LogoutMessage(Message message) {
		this(message.getContent());
	}
	
	public LogoutMessage(String content) {
		super(content);
		logoutMessage = JsonUtils.toBean(content, InnerMessage.class);
	}
	
	public LogoutMessage(String token, List<String> logoutUrls) {
		logoutMessage = new InnerMessage(token, logoutUrls);
		refreshContent();
	}
	
	public String getToken() {
		return logoutMessage.getToken();
	}

	public List<String> getLogoutUrls() {
		return logoutMessage.getLogoutUrls();
	}
	
	public void setToken(String token) {
		logoutMessage.setToken(token);
		refreshContent();
	}
	
	public void setLogoutUrls(List<String> logoutUrls) {
		logoutMessage.setLogoutUrls(logoutUrls);
		refreshContent();
	}
	
	private void refreshContent() {
		setContent(JsonUtils.toJSONString(logoutMessage));
	}
	
	public static class InnerMessage {

		private String token;
		
		private List<String> logoutUrls;
		
		public InnerMessage() {}
		
		public InnerMessage(String token, List<String> logoutUrls) {
			setToken(token);
			setLogoutUrls(logoutUrls);
		}
		
		public String getToken() {
			return token;
		}

		public void setToken(String token) {
			this.token = token;
		}

		public List<String> getLogoutUrls() {
			return logoutUrls;
		}

		public void setLogoutUrls(List<String> logoutUrls) {
			this.logoutUrls = logoutUrls;
		}
	}
}
