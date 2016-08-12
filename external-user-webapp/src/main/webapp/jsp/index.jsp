<%@ taglib uri="http://java.sun.com/portlet_2_0" prefix="portlet"%>
<%@ page import="java.net.URLEncoder"%>
<%@ page import="javax.servlet.http.Cookie"%>
<%@ page import="org.exoplatform.web.login.LoginError"%>
<%@ page import="org.exoplatform.container.PortalContainer"%>
<%@ page import="org.exoplatform.services.resources.ResourceBundleService"%>
<%@ page import="org.gatein.security.oauth.spi.OAuthProviderType"%>
<%@ page import="org.gatein.security.oauth.spi.OAuthProviderTypeRegistry"%>
<%@ page import="java.util.ResourceBundle"%>
<%@ page import="org.gatein.common.text.EntityEncoder"%>
<%@ page language="java"%>
<%
  String contextPath = request.getContextPath() ;
  PortalContainer portalContainer = PortalContainer.getCurrentInstance(session.getServletContext());
  ResourceBundleService service = (ResourceBundleService) portalContainer.getComponentInstanceOfType(ResourceBundleService.class);
  ResourceBundle res = service.getResourceBundle(service.getSharedResourceBundleNames(), request.getLocale());
%>

<script>
  require(["SHARED/jquery", "SHARED/I18NMessage"], function($, msg) {
  	$( document ).ready(function() {
        $.get( "/"+eXo.env.portal.rest+"/externalUser/bundle?locale=" + eXo.env.portal.language)
          .done(function(data) {
          	window.eXo.i18n.I18NMessage.externaluser = data;
          	window.bundleLoaded = true;
          	window.bundleLoading = false;
          	if($("form#UISpaceMember .addInvite").length > 0 && $("#inviteExternalUser").length == 0) {
          		$("form#UISpaceMember .addInvite").append( '<button class="btn" type="button" id="inviteExternalUser"  onclick="window.inviteExternalUser.showPopin()">' + msg.getMessage('externaluser.inviteExternal') + '</button>' );
          	}
         });
  	});
  });
</script>
<div class="uiBox externaluser-container" id="inviteExternalUserModal" style="margin:10px;">
  <div class="tab-content">
    <div class="modal-content">
      <div class="modal-header roundedTop">
        <h4 class="modal-title"><%=res.getString("externaluser.msg.inviteUserTitle") %></h4>
      </div>
      <div class="modal-body">
        <div id="inviteExternalUserMessage" style="display: none;"></div>
        <div id="inviteExternalUserForm" class="form-horizontal">
          <div class="control-group">
            <label class="control-label" for="firstName"><%=res.getString("externaluser.msg.firstName") %>:</label>
            <div class="controls">
              <input name="firstName" type="text" id="firstName">
              *
            </div>
          </div>
          <div class="control-group">
            <label class="control-label" for="lastName"><%=res.getString("externaluser.msg.lastName") %>:</label>
            <div class="controls">
              <input name="lastName" type="text" id="lastName">
              *
            </div>
          </div>
          <div class="control-group">
            <label class="control-label" for="email"><%=res.getString("externaluser.msg.mail") %>:</label>
            <div class="controls">
              <input name="email" type="text" id="email">
              *
            </div>
          </div>
        </div>
      </div>
      <div id="inviteExternalUserButtons" class="modal-footer roundedBottom center">
        <button type="button" id="inviteUser" class="btn btn-primary input-large" onclick="window.inviteExternalUser.invite()"><%=res.getString("externaluser.msg.invite")%></button>
      </div>
    </div>
  </div>
</div>