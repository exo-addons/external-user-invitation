package org.exoplatform.addon.externaluser;

import org.exoplatform.commons.chromattic.ChromatticManager;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.web.security.codec.CodecInitializer;
import org.exoplatform.web.security.security.CookieTokenService;
import org.exoplatform.web.security.security.TokenServiceInitializationException;

public class ExternalUserTokenService extends CookieTokenService {

  public ExternalUserTokenService(InitParams initParams, ChromatticManager chromatticManager, CodecInitializer codecInitializer) throws TokenServiceInitializationException {
    super(initParams, chromatticManager, codecInitializer);
  }

}
