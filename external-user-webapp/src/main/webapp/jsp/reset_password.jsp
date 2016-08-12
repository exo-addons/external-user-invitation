<%@ page import="java.net.URLEncoder"%>
<%@ page import="javax.servlet.http.Cookie"%>
<%@ page import="org.exoplatform.web.login.LoginError"%>
<%@ page import="org.exoplatform.container.PortalContainer"%>
<%@ page import="org.exoplatform.services.resources.ResourceBundleService"%>
<%@ page import="org.gatein.security.oauth.spi.OAuthProviderType"%>
<%@ page import="org.gatein.security.oauth.spi.OAuthProviderTypeRegistry"%>
<%@ page import="org.exoplatform.portal.resource.SkinService"%>
<%@ page import="java.util.ResourceBundle"%>
<%@ page import="org.gatein.common.text.EntityEncoder"%>
<%@ page import="org.exoplatform.web.login.recovery.PasswordRecoveryService" %>
<%@ page import="org.exoplatform.web.controller.QualifiedName" %>
<%@ page language="java" %>
<%
  String username = request.getParameter("username");
  if(username == null) {
      username = "";
  } else {
      EntityEncoder encoder = EntityEncoder.FULL;
      username = encoder.encode(username);
  }

  PortalContainer portalContainer = PortalContainer.getCurrentInstance(session.getServletContext());
  ResourceBundleService service = (ResourceBundleService) portalContainer.getComponentInstanceOfType(ResourceBundleService.class);
  ResourceBundle res = service.getResourceBundle(service.getSharedResourceBundleNames(), request.getLocale());
  response.setCharacterEncoding("UTF-8"); 
  response.setContentType("text/html; charset=UTF-8");
  SkinService skinService = (SkinService) PortalContainer.getCurrentInstance(session.getServletContext())
                          .getComponentInstanceOfType(SkinService.class);
  String loginCssPath = skinService.getSkin("portal/login", "Default").getCSSPath();
  String tokenId = (String) request.getAttribute("tokenId");
%>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
  <head>
    <title><%=res.getString("gatein.forgotPassword.changePass")%></title>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>   
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <link rel="shortcut icon" type="image/x-icon"  href="/portal/favicon.ico" />
    <link href="<%=loginCssPath%>" rel="stylesheet" type="text/css"/>
    <script type="text/javascript" src="/platform-extension/javascript/jquery-1.7.1.js"></script>
  </head>
  <body>
    <div class="loginBGLight"><span></span></div>
    <div class="uiLogin">
      <div class="loginContainer">
        <div class="loginHeader introBox">
          <div class="userLoginIcon"><%=res.getString("gatein.forgotPassword.changePass")%></div>
        </div>
        <div class="loginContent">
        <div class="titleLogin">
        </div>
        <div class="centerLoginContent">
			<form class="UIForm" id="changePassForm" name="changePassForm" action="/portal/changePass"" method="post">
				<input type="hidden" name="tokenId" id="tokenId" value="<%=tokenId%>">
				<input class="password newPassword" tabindex="2" id="UIPortalLoginFormControl" type="password" id="password" name="password" placeholder="<%=res.getString("gatein.forgotPassword.newPassword")%>" onblur="this.placeholder = '<%=res.getString("gatein.forgotPassword.newPassword")%>'" onfocus="this.placeholder = ''"/>
				<input class="password newConfirmPassword" tabindex="2" id="UIPortalLoginFormControl" type="password" id="confirmpassword" name="confirmpassword" placeholder="<%=res.getString("gatein.forgotPassword.confirmNewPassword")%>" onblur="this.placeholder = '<%=res.getString("gatein.forgotPassword.confirmNewPassword")%>'" onfocus="this.placeholder = ''"/>
    			  <div id="UIPortalLoginFormAction" class="loginButton">
    				<button type="submit" class="button" tabindex="4"><%=res.getString("externaluser.msg.login")%></button>
    			  </div>
			</form>
        </div>
      </div>
      </div>
      <div id="platformInfoDiv" data-labelfooter="<%=res.getString("portal.login.Footer")%>" ></div>
    </div>
    <script>
    	$( document ).ready(function() {
          $( "#changePassForm" ).submit(function( event ) {
            if($("input.newConfirmPassword").val() === $("input.newPassword").val()) {
      			var passReg = /^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=]).{8,30}$/;
      			if(!passReg.test( $("input.newPassword").val() )) {
                    $(".titleLogin").html("<div class='signinFail'><i class='uiIconError'></i><%=res.getString("externaluser.msg.invalidPassswordFormat")%></div>");
                    event.preventDefault();
      			}
            } else {
              $(".titleLogin").html("<div class='signinFail'><i class='uiIconError'></i><%=res.getString("UIAccountForm.msg.password-is-not-match")%></div>");
              event.preventDefault();
            }
          });
      });
    </script>
  </body>
</html>