External user Addon
====================================

This addon allows to invite external users to join the intranet

For more information:

https://community.exoplatform.com/portal/intranet/wiki/group/spaces/external_users_addin/WikiHome
https://community.exoplatform.com/portal/g/:spaces:external_users_addin/external_users_addin/wiki

Invitation Email Templates:

* Invitation Template: /sites/shared/web contents/site artifacts/invitation.txt (This path can be changed by properties.)
* Activation Template: /sites/shared/web contents/site artifacts/activation.txt (This path can be changed by properties.)

Template keywords that will be replaced dynamically:

[SENDER] : Manager user that has sent the invitation.

[USERNAME] : external user login

[USER_FULLNAME]: external user display name

[SENDER_TITLE]: Manager user social position

[SPACE_NAME]: space where the external user was invited

[LINK]: activation link

[PASSWORD]: password of the external user
