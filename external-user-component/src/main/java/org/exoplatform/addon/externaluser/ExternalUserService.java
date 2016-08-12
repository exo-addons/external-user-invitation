package org.exoplatform.addon.externaluser;

import java.security.SecureRandom;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Calendar;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Random;
import java.util.ResourceBundle;

import javax.annotation.security.RolesAllowed;
import javax.jcr.Node;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.picocontainer.Startable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ibm.icu.text.Transliterator;

import org.exoplatform.commons.api.notification.plugin.NotificationPluginUtils;
import org.exoplatform.commons.api.settings.SettingService;
import org.exoplatform.commons.api.settings.SettingValue;
import org.exoplatform.commons.api.settings.data.Context;
import org.exoplatform.commons.api.settings.data.Scope;
import org.exoplatform.commons.utils.ISO8601;
import org.exoplatform.commons.utils.ListAccess;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.services.mail.MailService;
import org.exoplatform.services.organization.Group;
import org.exoplatform.services.organization.MembershipType;
import org.exoplatform.services.organization.OrganizationService;
import org.exoplatform.services.organization.Query;
import org.exoplatform.services.organization.User;
import org.exoplatform.services.organization.UserStatus;
import org.exoplatform.services.rest.resource.ResourceContainer;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.services.security.Identity;
import org.exoplatform.services.security.MembershipEntry;
import org.exoplatform.services.wcm.utils.WCMCoreUtils;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.web.CacheUserProfileFilter;

@Path("externalUser")
public class ExternalUserService implements ResourceContainer, Startable {
  private static final Logger                LOG                         = LoggerFactory.getLogger(ExternalUserService.class);

  public final static String                 EXO_SHOULD_CHANGE_PASS      = "exo:shouldChangePassword";

  public final static String                 EXO_EXPIRATION_DATE         = "exo:passwordExpirationDate";

  private static final Random                RANDOM                      = new SecureRandom();

  public static final int                    PASSWORD_LENGTH             = 8;

  public static final String                 NEW_USER_INVITED_EVENT      = "ExternalUser.event.userInvited";

  private static final String                USER_INVITATION_FAIL_EVENT  = "ExternalUser.event.userInvitationMailFail";

  public static final String                 EXTERNAL_USER_CREATED_EVENT = "ExternalUser.event.userCreated";

  private static final DateFormat            DATE_FORMAT                 = new SimpleDateFormat(ISO8601.COMPLETE_DATETIMEMSZRFC822_FORMAT);

  private PortalContainer                    container;

  private OrganizationService                organizationService;

  private SpaceService                       spaceService;

  private ListenerService                    listenerService;

  private IdentityManager                    identityManager;

  private MailService                        mailService;

  private SettingService                     settingService;

  private static Map<String, ResourceBundle> BUNDLE                      = new HashMap<String, ResourceBundle>();

  private boolean                            assignUserToSpecificGroup   = false;

  private boolean                            userSenderMail              = false;

  private boolean                            enableSpaceInvitation       = false;

  private String                             generatedUsernamePatttern;

  private String                             spaceAllowedRole;

  private String                             adminAllowedPermission;

  private String                             externalUsersGroupId;

  private String                             externalUsersMembershipTypeId;

  private Group                              externalUsersGroup;

  private MembershipType                     externalUsersMembershipType;

  private int                                disableUserPeriodInDays;

  String                                     invitationTemplateLocation;

  String                                     activationTemplateLocation;

  public ExternalUserService(InitParams initParams) {
    if (initParams.containsKey("invite.externaluser.activationTemplate")) {
      activationTemplateLocation = initParams.getValueParam("invite.externaluser.activationTemplate").getValue();
    }

    if (initParams.containsKey("invite.externaluser.invitationTemplate")) {
      invitationTemplateLocation = initParams.getValueParam("invite.externaluser.invitationTemplate").getValue();
    }
    if (StringUtils.isBlank(invitationTemplateLocation)) {
      LOG.error("Mandatory parameter 'invite.externaluser.invitationTemplate' is missing. Invitation template JCR path is required");
    }
    if (initParams.containsKey("invite.externaluser.useInvitationSenderMail")) {
      userSenderMail = initParams.getValueParam("invite.externaluser.useInvitationSenderMail").getValue().equals("true");
    }

    if (initParams.containsKey("invite.externaluser.usernamePattern")) {
      generatedUsernamePatttern = initParams.getValueParam("invite.externaluser.usernamePattern").getValue();
    }
    if (StringUtils.isBlank(generatedUsernamePatttern)) {
      LOG.error("Mandatory parameter 'invite.externaluser.usernamePattern' is undefined");
    }

    if (initParams.containsKey("invite.externaluser.space.enableInvitation")) {
      String enableSpaceInvitationString = initParams.getValueParam("invite.externaluser.space.enableInvitation").getValue();
      enableSpaceInvitation = !StringUtils.isEmpty(enableSpaceInvitationString) && enableSpaceInvitationString.equals("true");
    }

    if (initParams.containsKey("invite.externaluser.space.role")) {
      spaceAllowedRole = initParams.getValueParam("invite.externaluser.space.role").getValue();
    }
    if (StringUtils.isBlank(spaceAllowedRole)) {
      LOG.error("Mandatory parameter 'invite.externaluser.space.role' is undefined");
    }
    if (initParams.containsKey("invite.externaluser.permission")) {
      adminAllowedPermission = initParams.getValueParam("invite.externaluser.permission").getValue();
    }
    if (StringUtils.isBlank(adminAllowedPermission)) {
      LOG.error("Mandatory parameter 'invite.externaluser.permission' is undefined");
    }
    if (initParams.containsKey("invite.externaluser.assignToSpecificGroup")) {
      String assignUserToSpecificGroupString = initParams.getValueParam("invite.externaluser.assignToSpecificGroup").getValue();
      if (!StringUtils.isBlank(assignUserToSpecificGroupString)) {
        assignUserToSpecificGroup = Boolean.parseBoolean(assignUserToSpecificGroupString);
      }
    }
    if (assignUserToSpecificGroup) {
      if (initParams.containsKey("invite.externaluser.groupname")) {
        externalUsersGroupId = initParams.getValueParam("invite.externaluser.groupname").getValue();
      }
      if (StringUtils.isBlank(externalUsersGroupId)) {
        LOG.error("Mandatory parameter 'invite.externaluser.groupname' is undefined");
      }
      if (initParams.containsKey("invite.externaluser.membershipType")) {
        externalUsersMembershipTypeId = initParams.getValueParam("invite.externaluser.membershipType").getValue();
      }
      if (StringUtils.isBlank(externalUsersMembershipTypeId)) {
        LOG.error("Mandatory parameter 'invite.externaluser.membershipType' is undefined");
      }
    }

    if (initParams.containsKey("invite.externaluser.disableUserPeriod")) {
      String disableUserPeriodString = initParams.getValueParam("invite.externaluser.disableUserPeriod").getValue();
      if (!StringUtils.isBlank(disableUserPeriodString)) {
        disableUserPeriodInDays = Integer.parseInt(disableUserPeriodString);
      }
    }
  }

  @Override
  public void start() {
    if (container == null) {
      container = PortalContainer.getInstance();
      this.organizationService = container.getComponentInstanceOfType(OrganizationService.class);
      this.spaceService = container.getComponentInstanceOfType(SpaceService.class);
      this.listenerService = container.getComponentInstanceOfType(ListenerService.class);
      this.identityManager = container.getComponentInstanceOfType(IdentityManager.class);
      this.mailService = container.getComponentInstanceOfType(MailService.class);
      this.settingService = container.getComponentInstanceOfType(SettingService.class);
    }

    if (!StringUtils.isBlank(externalUsersMembershipTypeId)) {
      try {
        externalUsersMembershipType = organizationService.getMembershipTypeHandler()
                                                         .findMembershipType(externalUsersMembershipTypeId);
      } catch (Exception e) {
        LOG.error("Error while getting membership type with id " + externalUsersMembershipTypeId, e);
      }
    }
    if (externalUsersMembershipType == null) {
      LOG.error("MembershipType '{}' is not found, cannot add users to group with MembershipType", externalUsersMembershipTypeId);
    }

    if (!StringUtils.isBlank(externalUsersGroupId)) {
      try {
        externalUsersGroup = organizationService.getGroupHandler().findGroupById(externalUsersGroupId);
      } catch (Exception e) {
        LOG.error("Error while getting group with id " + externalUsersGroupId, e);
      }
    }
    if (externalUsersGroup == null) {
      LOG.error("External users group '{}' is not found, cannot add users to group", externalUsersGroupId);
    }
  }

  @Override
  public void stop() {
  }

  @GET
  @Path("bundle")
  @RolesAllowed("users")
  @Produces(MediaType.APPLICATION_JSON)
  public String getBundle(@QueryParam("locale") String locale) {
    try {
      if (BUNDLE.get(locale) == null) {
        BUNDLE.put(locale, getResourceBundle(new Locale(locale)));
      }
      JSONObject data = new JSONObject();
      Enumeration<String> enumeration = BUNDLE.get(locale).getKeys();
      while (enumeration.hasMoreElements()) {
        String key = (String) enumeration.nextElement();
        try {
          data.put(key.replaceAll("(.*)\\.", ""), BUNDLE.get(locale).getObject(key));
        } catch (MissingResourceException e) {
          // Nothing to do, this happens sometimes
        }
      }
      return data.toString();
    } catch (Throwable e) {
      LOG.error("error while getting resource bundle", e);
      return null;
    }
  }

  @GET
  @Path("enableSpaceInvitation")
  @RolesAllowed("users")
  public Response enableSpaceInvitation() {
    return Response.ok("" + enableSpaceInvitation).build();
  }

  @POST
  @Path("invite")
  @RolesAllowed("users")
  public Response invite(@FormParam("pathname") String pathname,
                         @FormParam("firstName") String firstName,
                         @FormParam("lastName") String lastName,
                         @FormParam("email") String email) {

    // Check if the user is allowed to invite a user
    Identity currentIdentity = ConversationState.getCurrent().getIdentity();
    String spaceGroupId = null;
    if (pathname.contains(":spaces:")) {
      spaceGroupId = getSpaceGroupId(pathname);
      if (!currentIdentity.isMemberOf(spaceGroupId, spaceAllowedRole)) {
        return Response.status(403).build();
      }
    } else {
      if (!currentIdentity.isMemberOf(MembershipEntry.parse(adminAllowedPermission))) {
        return Response.status(403).build();
      }
    }

    try {
      // Search for a user using the same email address
      Query query = new Query();
      query.setEmail(email);
      ListAccess<User> findUsersByMailResult = organizationService.getUserHandler().findUsersByQuery(query, UserStatus.ANY);

      String username = null;
      User invitedUser = null;
      boolean newUser = false;
      // If the user doesn't exists
      if (findUsersByMailResult.getSize() == 0) {
        newUser = true;
        invitedUser = createNewUser(firstName, lastName, email);

        listenerService.broadcast(EXTERNAL_USER_CREATED_EVENT, invitedUser, invitedUser);

        username = invitedUser.getUserName();

        // Commit user creation
        RequestLifeCycle.end();

        LOG.info("User '{}' created", email);

        // restart transaction that will be committed on Request end in high
        // level API
        RequestLifeCycle.begin(container);
      } else if (findUsersByMailResult.getSize() == 1) {

        // If the user already exists
        if (spaceGroupId == null) {
          LOG.error("User '{}' already exists", email);
          return Response.status(500).build();
        }
        invitedUser = findUsersByMailResult.load(0, 1)[0];
        username = invitedUser.getUserName();
      } else {
        LOG.error("Can't invite user '{}' because the email was found more than once in DB", email);
        return Response.status(500).build();
      }

      Space space = null;
      if (spaceGroupId != null) {
        space = spaceService.getSpaceByGroupId(spaceGroupId);
        if (space == null) {
          LOG.error("Can't invite user '{}' because the space was not found", email);
          return Response.status(500).build();
        }

        if (spaceService.isMember(space, username) || spaceService.isInvitedUser(space, username)) {
          LOG.warn("User {} is already invited or member of the space {}", username, space.getDisplayName());
        } else {
          // invite user to join the space
          if (newUser) {
            spaceService.addMember(space, username);
          } else {
            spaceService.addInvitedUser(space, username);
          }
        }
      }

      if (newUser) {
        sendInvitationMail(invitedUser, space, invitationTemplateLocation, null);
        listenerService.broadcast(NEW_USER_INVITED_EVENT, invitedUser, space);
      }
      return Response.ok().build();
    } catch (Exception e) {
      LOG.error("An error occurred when saving external user", e);
      return Response.status(500).build();
    }
  }

  public void sendInvitationMail(User invitedUser, Space space, String mailTemplatePath, String link) throws Exception {
    // Send mail which content comes from JCR
    Node node = (Node) WCMCoreUtils.getRepository().getSystemSession("collaboration").getItem(mailTemplatePath);

    User managerUser = (User) ConversationState.getCurrent().getAttribute(CacheUserProfileFilter.USER_PROFILE);
    if (managerUser == null) {
      managerUser = organizationService.getUserHandler().findUserByName(ConversationState.getCurrent().getIdentity().getUserId(),
                                                                        UserStatus.ANY);
      if (managerUser.getUserName().equals(invitedUser.getUserName())) {
        managerUser = null;
      }
    }

    String to = invitedUser.getEmail();
    String from = NotificationPluginUtils.getEmailFrom();
    if (userSenderMail && managerUser != null) {
      from = managerUser.getEmail();
    }

    String subject = node.getProperty("jcr:content/dc:title").getValues()[0].getString();
    String body = node.getProperty("jcr:content/jcr:data").getString();

    if (managerUser != null) {
      body = body.replace("[SENDER]", managerUser.getDisplayName());
    }

    body = body.replace("[USERNAME]", invitedUser.getUserName());
    body = body.replace("[USER_FULLNAME]", invitedUser.getDisplayName());

    if (body.contains("[SENDER_TITLE]")) {
      Profile managerProfile = identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME,
                                                                   managerUser.getUserName(),
                                                                   true).getProfile();
      String managerPosition = managerProfile.getPosition();
      if (!StringUtils.isBlank(managerPosition)) {
        body = body.replace("[SENDER_TITLE]", managerPosition);
      }
    }

    if (space != null) {
      body = body.replace("[SPACE_NAME]", space.getDisplayName());
    }

    if (link != null) {
      body = body.replace("[LINK]", link);
    }

    if (invitedUser.getPassword() != null) {
      body = body.replace("[PASSWORD]", invitedUser.getPassword());
    }

    try {
      mailService.sendMessage(from, to, subject, body);
    } catch (Exception e) {
      LOG.error("Error while sending mail to '" + invitedUser.getEmail() + "'", e);
      try {
        listenerService.broadcast(USER_INVITATION_FAIL_EVENT, invitedUser, e);
      } catch (Exception e1) {
        LOG.error("ERROR when broadcasting Mail Send Operation Error, cause: ", e1);
      }
      throw e;
    }
  }

  public Object getSetting(String userId, String key) {
    SettingValue<?> value = settingService.get(Context.USER.id(userId), Scope.GLOBAL, key);
    return (value == null) ? null : value.getValue();
  }

  public Group getExternalUsersGroup() {
    return externalUsersGroup;
  }

  public long getDisableUserPeriodInDays() {
    return disableUserPeriodInDays;
  }

  public boolean isAssignUserToSpecificGroup() {
    return assignUserToSpecificGroup;
  }

  public String getActivationTemplateLocation() {
    return activationTemplateLocation;
  }

  protected String generateUsername(String firstName, String lastName, String email) {
    String lastNameSimplified = deleteSpecialCharacters(lastName);
    String firstNameSimplified = deleteSpecialCharacters(firstName);
    String emailSimplified = deleteSpecialCharacters(email);

    String usernameBase = generatedUsernamePatttern.replace("firstName", firstNameSimplified);
    usernameBase = usernameBase.replace("lastName", lastNameSimplified);
    usernameBase = usernameBase.replace("email", emailSimplified);

    return usernameBase;
  }

  protected ResourceBundle getResourceBundle(Locale locale) {
    return ResourceBundle.getBundle("locale.addon.externaluser", locale, this.getClass().getClassLoader());
  }

  private String getSpaceGroupId(String pathname) {
    String spaceGroupId;
    int indexOfSpacesParent = pathname.indexOf(":spaces:");
    int indexOfEndOfSpaceName = pathname.indexOf('/', indexOfSpacesParent);
    spaceGroupId = pathname.substring(indexOfSpacesParent, indexOfEndOfSpaceName);
    spaceGroupId = spaceGroupId.replaceAll(":", "/");
    return spaceGroupId;
  }

  private User createNewUser(String firstName, String lastName, String email) throws Exception {
    String usernameBase = generateUsername(firstName, lastName, email);

    int index = 1;
    String username = getUsername(usernameBase, usernameBase, index);
    User invitedUser = organizationService.getUserHandler().createUserInstance(username);
    invitedUser.setFirstName(firstName);
    invitedUser.setLastName(lastName);
    invitedUser.setEmail(email);
    String password = generateRandomPassword();
    invitedUser.setPassword(password);
    invitedUser.setDisplayName(firstName + " " + lastName);
    organizationService.getUserHandler().createUser(invitedUser, true);
    if (assignUserToSpecificGroup && externalUsersMembershipType != null && externalUsersGroup != null) {
      organizationService.getMembershipHandler().linkMembership(invitedUser,
                                                                externalUsersGroup,
                                                                externalUsersMembershipType,
                                                                true);
    }
    return invitedUser;
  }

  private String deleteSpecialCharacters(String usernameBase) {
    Transliterator accentsconverter = Transliterator.getInstance("NFD; [:Nonspacing Mark:] Remove; NFC");
    usernameBase = accentsconverter.transliterate(usernameBase.toLowerCase());
    return usernameBase.replaceAll("[^a-zA-Z0-9]", "");
  }

  private String getUsername(String username, String usernameBase, int index) throws Exception {
    User user = organizationService.getUserHandler().findUserByName(username, UserStatus.ANY);
    if (user != null) {
      username = usernameBase + "." + (++index);
      username = getUsername(username, usernameBase, index);
    }
    return username;
  }

  private static String generateRandomPassword() {
    String letters = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKMNPQRSTUVWXYZ23456789+@";

    String pw = "";
    for (int i = 0; i < PASSWORD_LENGTH; i++) {
      int index = (int) (RANDOM.nextDouble() * letters.length());
      pw += letters.substring(index, index + 1);
    }
    return pw;
  }

  public Calendar addPasswordExpiration(String userId, Date date) {
    Calendar calendar = Calendar.getInstance();
    calendar.setTime(date);
    calendar.add(Calendar.HOUR, 24 * disableUserPeriodInDays);
    date = calendar.getTime();
    settingService.set(Context.USER.id(userId),
                       Scope.GLOBAL,
                       EXO_EXPIRATION_DATE,
                       new SettingValue<String>(DATE_FORMAT.format(date)));
    settingService.set(Context.USER.id(userId), Scope.GLOBAL, EXO_SHOULD_CHANGE_PASS, new SettingValue<Boolean>(false));
    return calendar;
  }

  public void addPasswordExpiration(ConversationState state, Date date) {
    String userId = state.getIdentity().getUserId();
    Calendar calendar = addPasswordExpiration(userId, date);
    state.setAttribute(EXO_EXPIRATION_DATE, calendar);
    state.setAttribute(EXO_SHOULD_CHANGE_PASS, false);
  }

  public boolean hasPasswordExpired(String userId) {
    Date passwordExpirationDate = getPasswordExpirationDate(userId);
    if (passwordExpirationDate == null) {
      throw new IllegalStateException("User password expiration date is not valid, null");
    }
    Calendar passwordExpirationCalendar = Calendar.getInstance();
    passwordExpirationCalendar.setTime(passwordExpirationDate);
    return Calendar.getInstance().after(passwordExpirationCalendar);
  }

  public boolean hasPasswordExpired(ConversationState state) {
    Calendar passwordExpirationCalendar = (Calendar) state.getAttribute(EXO_EXPIRATION_DATE);
    if (passwordExpirationCalendar == null) {
      Date passwordExpirationDate = getPasswordExpirationDate(state.getIdentity().getUserId());
      if (passwordExpirationDate == null) {
        throw new IllegalStateException("User password expiration date is not valid, null");
      } else {
        passwordExpirationCalendar = Calendar.getInstance();
        passwordExpirationCalendar.setTime(passwordExpirationDate);
        state.setAttribute(EXO_EXPIRATION_DATE, passwordExpirationCalendar);
      }
    }
    return Calendar.getInstance().after(passwordExpirationCalendar);
  }

  public boolean isUserExternal(ConversationState state, String userId) {
    boolean isExternal = false;
    // Test here if external group is empty to display the warning message
    // only once on startup, else it will be repeated each HTTP request
    if (!isAssignUserToSpecificGroup()) {
      LOG.warn("'Auto disable' External User Feature cannot be enabled. Please enable the 'assignment of external users to a group' feature.");
    }
    if (getExternalUsersGroup() == null) {
      LOG.warn("'Auto disable' External User Feature cannot be enabled. Please specify a valid External Users Group.");
    }
    if (getExternalUsersGroup() != null && isAssignUserToSpecificGroup()) {
      Group externalUsersGroup = getExternalUsersGroup();
      String extGroupId = externalUsersGroup.getId();
      isExternal = state.getIdentity().isMemberOf(extGroupId);
    }
    return isExternal;
  }

  public boolean shouldChangePassword(ConversationState state) {
    Boolean shouldChangePass = (Boolean) state.getAttribute(EXO_SHOULD_CHANGE_PASS);
    if (shouldChangePass == null) {
      String userId = state.getIdentity().getUserId();
      shouldChangePass = shouldChangePassword(userId);
      if (shouldChangePass == null) {
        throw new IllegalStateException("User password state is null");
      } else {
        state.setAttribute(EXO_SHOULD_CHANGE_PASS, shouldChangePass);
      }
    }
    return shouldChangePass;
  }

  public boolean shouldChangePassword(String userId) {
    Boolean setting = (Boolean) getSetting(userId, EXO_SHOULD_CHANGE_PASS);
    return (setting == null || setting);
  }

  public Date getPasswordExpirationDate(String userId) {
    String passwordExpirationString = (String) getSetting(userId, EXO_EXPIRATION_DATE);
    if (passwordExpirationString == null) {
      throw new IllegalStateException("User password expiration date is not valid, null");
    }
    Date passwordExpirationDate = null;
    try {
      passwordExpirationDate = DATE_FORMAT.parse(passwordExpirationString);
    } catch (Exception e) {
      throw new IllegalStateException("User password expiration date is not valid, " + passwordExpirationString);
    }
    return passwordExpirationDate;
  }

  public static void main(String[] args) {
    System.out.println(Base64.getEncoder().encodeToString("passwordExpirationDate".getBytes()));
  }
}
