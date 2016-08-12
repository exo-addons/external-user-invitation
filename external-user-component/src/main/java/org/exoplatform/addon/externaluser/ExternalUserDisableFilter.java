package org.exoplatform.addon.externaluser;

import java.io.IOException;
import java.util.Calendar;

import javax.servlet.FilterChain;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.lang.StringUtils;
import org.gatein.wci.security.Credentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.services.organization.OrganizationService;
import org.exoplatform.services.organization.User;
import org.exoplatform.services.organization.UserStatus;
import org.exoplatform.services.security.Authenticator;
import org.exoplatform.services.security.ConversationRegistry;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.services.security.Identity;
import org.exoplatform.services.security.IdentityRegistry;
import org.exoplatform.services.security.StateKey;
import org.exoplatform.services.security.web.HttpSessionStateKey;
import org.exoplatform.web.filter.Filter;
import org.exoplatform.web.security.security.RemindPasswordTokenService;

public class ExternalUserDisableFilter implements Filter {
  private static final Logger        LOG = LoggerFactory.getLogger(ExternalUserDisableFilter.class);

  private ExternalUserService        externalUserService;

  private OrganizationService        organizationService;

  private ConversationRegistry       conversationRegistry;

  private IdentityRegistry           identityRegistry;

  private ExternalUserTokenService   tokenService;

  private Authenticator              authenticator;

  private RemindPasswordTokenService remindPasswordTokenService;

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
    if (externalUserService == null) {
      externalUserService = PortalContainer.getInstance().getComponentInstanceOfType(ExternalUserService.class);
      tokenService = PortalContainer.getInstance().getComponentInstanceOfType(ExternalUserTokenService.class);
      organizationService = PortalContainer.getInstance().getComponentInstanceOfType(OrganizationService.class);
      conversationRegistry = PortalContainer.getInstance().getComponentInstanceOfType(ConversationRegistry.class);
      identityRegistry = PortalContainer.getInstance().getComponentInstanceOfType(IdentityRegistry.class);
      authenticator = PortalContainer.getInstance().getComponentInstanceOfType(Authenticator.class);
      remindPasswordTokenService = PortalContainer.getInstance().getComponentInstanceOfType(RemindPasswordTokenService.class);
    }

    if (externalUserService.getDisableUserPeriodInDays() <= 0) {
      chain.doFilter(request, response);
      return;
    }

    if (!(request instanceof HttpServletRequest)) {
      chain.doFilter(request, response);
      return;
    }

    HttpServletRequest httpRequest = (HttpServletRequest) request;
    HttpServletResponse httpResponse = (HttpServletResponse) response;
    ConversationState state = getCurrentState(httpRequest);

    if (httpRequest.getRequestURI().contains("/changePass") && httpRequest.getParameter("tokenId") != null
        && httpRequest.getParameter("password") != null) {
      changePassword(httpRequest, httpResponse, state);
      httpResponse.sendRedirect("/");
      return;
    }

    if (httpRequest.getRequestURI().contains("/activateUser") && httpRequest.getParameter("tokenId") != null) {
      boolean userActivated = activateUser(httpRequest, httpResponse);
      if (userActivated) {
        PortalContainer pContainer = PortalContainer.getInstance();
        ServletContext context = pContainer.getPortalContext();
        context.getRequestDispatcher("/jsp/userActivatedAgain.jsp").forward(httpRequest, httpResponse);
      } else {
        httpResponse.sendRedirect("/");
      }
      return;
    }

    String userId = httpRequest.getRemoteUser();
    if (!StringUtils.isBlank(userId)) {
      RequestLifeCycle.begin(PortalContainer.getInstance());
      try {
        boolean isExternal = externalUserService.isUserExternal(state, userId);
        if (isExternal) {
          boolean changePassword = externalUserService.shouldChangePassword(state);
          if (changePassword) {
            Credentials credentials = new Credentials(userId, "");
            String tokenId = remindPasswordTokenService.createToken(credentials);
            httpRequest.setAttribute("tokenId", tokenId);
            PortalContainer pContainer = PortalContainer.getInstance();
            ServletContext context = pContainer.getPortalContext();
            context.getRequestDispatcher("/jsp/reset_password.jsp").forward(httpRequest, httpResponse);
            return;
          }

          boolean passwordExpired = externalUserService.hasPasswordExpired(userId);
          if (passwordExpired) {
            try {
              organizationService.getUserHandler().setEnabled(userId, false, true);
              sendActivationMail(httpRequest, userId);
            } catch (Exception e) {
              throw new RuntimeException("Can't disable user '" + userId + "' that has an expired password", e);
            }
            PortalContainer pContainer = PortalContainer.getInstance();
            ServletContext context = pContainer.getPortalContext();
            context.getRequestDispatcher("/jsp/userPasswordExpired.jsp").forward(httpRequest, httpResponse);
            httpRequest.getSession(true).invalidate();
            return;
          }
        }
      } finally {
        RequestLifeCycle.end();
      }
    }
    chain.doFilter(request, response);
  }

  private void sendActivationMail(HttpServletRequest httpRequest, String userId) throws Exception {
    Credentials credentials = new Credentials(userId, "");
    String tokenId = tokenService.createToken(credentials);

    String host = httpRequest.getScheme() + "://" + httpRequest.getServerName() + ":" + httpRequest.getServerPort();
    String activeLink = host + httpRequest.getServletContext().getContextPath() + "/activateUser?tokenId=" + tokenId;

    User invitedUser = organizationService.getUserHandler().findUserByName(userId, UserStatus.ANY);

    externalUserService.sendInvitationMail(invitedUser, null, externalUserService.getActivationTemplateLocation(), activeLink);
  }

  private boolean activateUser(HttpServletRequest httpRequest, HttpServletResponse httpResponse) throws IOException {
    String tokenId = httpRequest.getParameter("tokenId");
    Credentials credentials = tokenService.validateToken(tokenId, true);
    if (credentials == null) {
      return false;
    }
    String userId = credentials.getUsername();
    try {
      organizationService.getUserHandler().setEnabled(userId, true, true);
    } catch (Exception e) {
      LOG.error("Error when enabling user '" + userId + "'", e);
    }
    externalUserService.addPasswordExpiration(userId, Calendar.getInstance().getTime());
    return true;
  }

  private void changePassword(HttpServletRequest httpRequest, HttpServletResponse httpResponse, ConversationState state) {
    String tokenId = httpRequest.getParameter("tokenId");
    String extUserId = state.getIdentity().getUserId();
    Credentials credentials = remindPasswordTokenService.validateToken(tokenId, true);
    if (credentials != null && credentials.getUsername().equals(extUserId)) {
      String password = httpRequest.getParameter("password");
      try {
        User user = organizationService.getUserHandler().findUserByName(extUserId, UserStatus.ANY);
        user.setPassword(password);
        organizationService.getUserHandler().saveUser(user, false);
        externalUserService.addPasswordExpiration(state, Calendar.getInstance().getTime());
      } catch (Exception e) {
        LOG.error("Error while changing user '" + extUserId + "' password.", e);
      }
    }
  }

  private ConversationState getCurrentState(HttpServletRequest httpRequest) {
    ConversationState state = null;
    String userId = httpRequest.getRemoteUser();
    if (userId != null) {
      HttpSession httpSession = httpRequest.getSession();
      StateKey stateKey = new HttpSessionStateKey(httpSession);
      state = conversationRegistry.getState(stateKey);
      if (state == null) {
        Identity identity = identityRegistry.getIdentity(userId);
        if (identity == null) {
        } else {
          state = new ConversationState(identity);
          try {
            identity = authenticator.createIdentity(userId);
            state = new ConversationState(identity);
          } catch (Exception e) {
            throw new RuntimeException("Unable restore identity of user '" + userId + "'", e);
          }
        }
      }
    }
    return state;
  }

}
