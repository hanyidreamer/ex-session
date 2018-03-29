package com.nameof.web.controller;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.socket.TextMessage;

import com.alibaba.fastjson.JSONObject;
import com.nameof.common.domain.User;
import com.nameof.common.utils.CookieUtil;
import com.nameof.common.utils.UrlBuilder;
import com.nameof.mq.message.LogoutMessage;
import com.nameof.mq.sender.Sender;
import com.nameof.service.UserService;
import com.nameof.web.custom.component.request.CustomHttpServletRequest;
import com.nameof.web.websocket.WsLoginHandler;

@Controller
public class SystemController {

	/** "记住我"过期策略为15天，作用于Cookie的maxAge，Session的MaxInactiveInterval */
	private static final int REMEMBER_LOGIN_STATE_TIME = 15 * 24 * 60 * 60;
	
	/** 票据传递参数名 */
	private static final String TICKET_KEY = "token";
	
	/** 返回地址参数名 */
	private static final String RETURN_URL_KEY = "returnUrl";
	
	/** 注销地址参数名 */
	private static final String LOGOUT_URL_KEY = "logoutUrl";
	
	private static final Charset URL_ENCODING_CHARSET = Charset.forName("UTF-8");
	
	protected final Logger logger = LoggerFactory.getLogger(getClass());
	
	@Autowired
	private Sender logoutMessageSender;
	
	@Autowired
	private UserService userService;
	
	@Autowired
	private WsLoginHandler wsLoginHandler;
	
	@Value("${login.websocket.enable}")
	private boolean loginWithWebSocket;

	@RequestMapping(value = "/login", method = RequestMethod.GET)
	public String login(String returnUrl,
						String logoutUrl,
					    HttpSession session,
					    HttpServletResponse response,
					    HttpServletRequest request,
					    Model model) throws IOException {
		User user = (User) session.getAttribute("user");
		if (user != null) {
			
			if (StringUtils.isNotBlank(returnUrl) && StringUtils.isNotBlank(logoutUrl)) {
				logger.debug("user {} login from {} logout url is {}", new Object[]{user.getName(), returnUrl, logoutUrl});
				
				//存储客户端注销地址
				storeLogoutUrl(session, logoutUrl);

				//直接携带token返回客户端站点
				backToClient(returnUrl, session, response);
				return null;
			}
			else {
				return "redirect:index";//不允许重复登录
			}
		}
		else {
			//返回地址、注销地址存入表单隐藏域
			model.addAttribute(RETURN_URL_KEY, returnUrl);
			model.addAttribute(LOGOUT_URL_KEY, logoutUrl);
		}
		return "login";
	}

	/**
	 * 处理网页登录
	 * @param username
	 * @param passwd
	 * @param rememberMe
	 * @param returnUrl
	 * @param session
	 * @param response
	 * @param request
	 * @param model
	 * @return
	 * @throws IOException
	 */
	@RequestMapping(value = "/processLogin", method = RequestMethod.POST)
	public String processLogin(User inputUser,
							   Boolean rememberMe,
							   String returnUrl,
							   String logoutUrl,
							   HttpSession session,
							   HttpServletResponse response,
							   HttpServletRequest request,
							   Model model) throws IOException {
		
		User user = userService.verifyUserLogin(inputUser);
		if (user == null) {
			//回传返回地址、注销地址到隐藏域
			model.addAttribute(RETURN_URL_KEY, returnUrl);
			model.addAttribute(LOGOUT_URL_KEY, logoutUrl);
			model.addAttribute("error", "用户名或密码错误!");
			return "login";
		}
		else {
			session.setAttribute("user", user);
			if (rememberMe == Boolean.TRUE) {
				//"记住我"
				session.setMaxInactiveInterval(REMEMBER_LOGIN_STATE_TIME);
				Cookie sessionCookie = CookieUtil.getCookie(request, CustomHttpServletRequest.COOKIE_SESSION_KEY);
				if (sessionCookie != null) {
					sessionCookie.setMaxAge(REMEMBER_LOGIN_STATE_TIME);
					response.addCookie(sessionCookie);
				}
			}

			if (StringUtils.isNotBlank(returnUrl) && StringUtils.isNotBlank(logoutUrl)) {
				logger.debug("user {} login from {} logout url is {}", new Object[]{user.getName(), returnUrl, logoutUrl});
			}
			else {
				logger.debug("user {} login", user.getName());
			}
			
			//存储客户端注销地址
			storeLogoutUrl(session, logoutUrl);
			
			//携带token返回客户端站点
			if (StringUtils.isNotBlank(returnUrl)) {
				backToClient(returnUrl, session, response);
				return null;
			}
			
			return "redirect:/index";
		}
	}

	/**
	 * 存储客户端站点登出地址到session
	 * @param session
	 * @param logoutUrl 登出地址
	 * @throws UnsupportedEncodingException
	 */
	private void storeLogoutUrl(HttpSession session, String logoutUrl) throws UnsupportedEncodingException {
		
		if (StringUtils.isBlank(logoutUrl))
			return;
		
		@SuppressWarnings("unchecked")
		List<String> logoutUrls = (List<String>) session.getAttribute(LOGOUT_URL_KEY);
		if (logoutUrls == null) {
			logoutUrls = new ArrayList<>();
		}
		
		logoutUrls.add(URLDecoder.decode(logoutUrl, URL_ENCODING_CHARSET.name()));
		
		//即时交互缓存的序列化实现的session，对象实例变化，此处需要重新set
		session.setAttribute(LOGOUT_URL_KEY, logoutUrls);
	}

	/**
	 * 携带token返回客户端站点
	 * @param returnUrl
	 * @param session
	 * @param response
	 * @throws IOException
	 */
	private void backToClient(String returnUrl, HttpSession session, HttpServletResponse response) throws IOException {
		UrlBuilder builder = UrlBuilder.parse(URLDecoder.decode(returnUrl, URL_ENCODING_CHARSET.name()));
		builder.addParameter(TICKET_KEY, session.getId());
		response.sendRedirect(builder.toString());
	}
	
	
	/**
	 * 注销全局会话，并向客户端站点发送注销消息
	 * 
	 * @param session
	 * @return
	 */
	@RequestMapping(value = "/logout")
	public String logout(HttpSession session, HttpServletResponse response) {
		@SuppressWarnings("unchecked")
		List<String> logoutUrls = (List<String>) session.getAttribute(LOGOUT_URL_KEY);

		//send logout message
		if (logoutUrls != null) {
			logoutMessageSender.sendMessage(new LogoutMessage(session.getId(), logoutUrls));
		}
		
		Cookie c = new Cookie(CustomHttpServletRequest.COOKIE_SESSION_KEY, "");
		c.setMaxAge(0);
		response.addCookie(c);
		
		session.invalidate();
		return "redirect:/login";
	}
	
	
	/**
	 * 为客户端站点验证token
	 * @param session
	 * @return JSON格式的用户信息
	 */
	@RequestMapping(value = "/validatetoken", method = RequestMethod.POST)
	@ResponseBody
	public Object validatetoken(HttpSession session) {
		return session.getAttribute("user");
	}
	
	/**
	 * 网页轮询验证扫码登录
	 * @param session
	 * @return 用户是否已登录
	 */
	@RequestMapping(value = "/verifyQRCodeLogin", method = RequestMethod.POST)
	@ResponseBody
	public Boolean verifyQRCodeLogin(HttpSession session) {
		if (session.getAttribute("user") == null) {
			return Boolean.FALSE;
		}
		else {
			return Boolean.TRUE;
		}
	}
	
	/**
	 * 处理手机客户端扫码登录
	 * @param username
	 * @param passwd
	 * @param session
	 * @return 登录结果提示消息
	 */
	@RequestMapping(value = "/processQRCodeLogin", method = RequestMethod.POST)
	@ResponseBody
	public String processQRCodeLogin(User inputUser, HttpSession session) {
		String msg = "";
		User user = userService.verifyUserLogin(inputUser);
		if (user == null) {
			msg = "用户名或密码错误!";
		}
		else {
			session.setAttribute("user", user);
			sendWebSocketMsgIfnecessary(session);
			msg = "登录成功!";
		}
		return msg;
	}

	/**
	 * 向浏览器推送发送当前session已登录
	 * @param session
	 */
	private void sendWebSocketMsgIfnecessary(HttpSession session) {
		if (loginWithWebSocket) {
			JSONObject json = new JSONObject();
			json.put("login", true);
			TextMessage text = new TextMessage(json.toString());
			wsLoginHandler.sendMessageToUser(session.getId(), text);
		}
	}
	
}