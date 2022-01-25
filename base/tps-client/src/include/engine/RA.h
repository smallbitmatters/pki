/* --- BEGIN COPYRIGHT BLOCK ---
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation;
 * version 2.1 of the License.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA  02110-1301  USA 
 * 
 * Copyright (C) 2007 Red Hat, Inc.
 * All rights reserved.
 * --- END COPYRIGHT BLOCK ---
 */

#ifndef RA_H
#define RA_H

#ifdef HAVE_CONFIG_H
#ifndef AUTOTOOLS_CONFIG_H
#define AUTOTOOLS_CONFIG_H

/* Eliminate warnings when using Autotools */
#undef PACKAGE_BUGREPORT
#undef PACKAGE_NAME
#undef PACKAGE_STRING
#undef PACKAGE_TARNAME
#undef PACKAGE_VERSION
#include <config.h>
#endif /* AUTOTOOLS_CONFIG_H */
#endif /* HAVE_CONFIG_H */

#include "pk11func.h"
#include "main/Base.h"
#include "main/ConfigStore.h"
#include "main/Buffer.h"
#include "main/LogFile.h"
#include "apdu/APDU.h"
#include "main/RA_Context.h"
#include "channel/Secure_Channel.h"
#include "cms/HttpConnection.h"
#include "cms/ConnectionInfo.h"

/*
 *
 * LL_PER_SERVER = 4        these messages will occur only once during the
 *                          entire invocation of the server, e.g. at startup
 *                          or shutdown time., reading the conf parameters.
 *                          Perhaps other infrequent events relating to
 *                          failing over of CA, TKS, too
 *
 * LL_PER_CONNECTION = 6    these messages happen once per connection - most
 *                          of the log events will be at this level
 *
 * LL_PER_PDU = 8           these messages relate to PDU processing. If you
 *                          have something that is done for every PDU, such
 *                          as applying the MAC, it should be logged at this
 *                          level
 *
 * LL_ALL_DATA_IN_PDU = 9   dump all the data in the PDU - a more chatty
 *                          version of the above
 */
enum RA_Log_Level {
	LL_PER_SERVER = 4,
	LL_PER_CONNECTION = 6,
	LL_PER_PDU = 8,
	LL_ALL_DATA_IN_PDU = 9
};

enum RA_Algs {
        ALG_RSA = 1,
        ALG_RSA_CRT = 2,
        ALG_DSA = 3,
        ALG_EC_F2M = 4,
        ALG_EC_FP = 5
};

#ifdef XP_WIN32
#define TPS_PUBLIC __declspec(dllexport)
#else /* !XP_WIN32 */
#define TPS_PUBLIC
#endif /* !XP_WIN32 */

#define TRANSPORT_KEY_NAME "sharedSecret"

typedef char NSSUTF8;

class RA
{
  public:
	  RA();
	  ~RA();
  public:

 	  static PK11SymKey *ComputeSessionKey(RA_Session *session,
                                           Buffer &CUID,
                                           Buffer &keyinfo,
                                           Buffer &card_challenge,
                                           Buffer &host_challenge,
                                           Buffer **host_cryptogram,
                                           Buffer &card_cryptogram,
                                           PK11SymKey **encSymKey,
                                           char** drm_kekSessionKey_s,
                                           char** kek_kekSessionKey_s,
                                           char **keycheck_s,
                                           const char *connId);
      static void ServerSideKeyGen(RA_Session *session, const char* cuid,
                                   const char *userid, char* kekSessionKey_s,
                                   char **publickey_s,
                                   char **wrappedPrivateKey_s,
                                   char **ivParam_s, const char *connId,
                                   bool archive, int keysize, bool isECC);
	  static void RecoverKey(RA_Session *session, const char* cuid,
                             const char *userid, char* kekSessionKey_s,
                             char *cert_s, char **publickey_s,
                             char **wrappedPrivateKey_s, const char *connId,  char **ivParam_s);

	  static Buffer *ComputeHostCryptogram(Buffer &card_challenge, Buffer &host_challenge);

          static PK11SymKey *FindSymKeyByName( PK11SlotInfo *slot, char *keyname);
          static PK11SymKey *CreateDesKey24Byte(PK11SlotInfo *slot, PK11SymKey *origKey);
  public:
	  TPS_PUBLIC static ConfigStore *GetConfigStore();
  public:
	  TPS_PUBLIC static void Error(const char *func_name, const char *fmt, ...);
          TPS_PUBLIC static void Debug(const char *func_name, const char *fmt, ...);
	  TPS_PUBLIC static void DebugBuffer(const char *func_name, const char *prefix, Buffer *buf);
	  TPS_PUBLIC static void Error(RA_Log_Level level, const char *func_name, const char *fmt, ...);
	  TPS_PUBLIC static void Debug(RA_Log_Level level, const char *func_name, const char *fmt, ...);
	  static void DebugBuffer(RA_Log_Level level, const char *func_name, const char *prefix, Buffer *buf);
  private:
	  static void ErrorThis(RA_Log_Level level, const char *func_name, const char *fmt, va_list ap);
	  static void DebugThis(RA_Log_Level level, const char *func_name, const char *fmt, va_list ap);
  public:
          static PRLock *GetVerifyLock();
  public:
          static HttpConnection *GetTKSConn(const char *id);
          static void ReturnTKSConn(HttpConnection *conn);

          static HttpConnection *GetDRMConn(const char *id);
          static void ReturnDRMConn(HttpConnection *conn);
          static int GetCurrentIndex(HttpConnection *conn);
  public: /* default values */
	  static const char *CFG_DEF_CARDMGR_INSTANCE_AID;
	  static const char *CFG_DEF_NETKEY_INSTANCE_AID;
	  static const char *CFG_DEF_NETKEY_FILE_AID;
	  static const char *CFG_DEF_NETKEY_OLD_INSTANCE_AID;
	  static const char *CFG_DEF_NETKEY_OLD_FILE_AID;
	  static const char *CFG_DEF_APPLET_SO_PIN;
  public:
	  static const char *CFG_APPLET_DELETE_NETKEY_OLD;
	  static const char *CFG_APPLET_CARDMGR_INSTANCE_AID;
	  static const char *CFG_APPLET_NETKEY_INSTANCE_AID;
	  static const char *CFG_APPLET_NETKEY_FILE_AID;
	  static const char *CFG_APPLET_NETKEY_OLD_INSTANCE_AID;
	  static const char *CFG_APPLET_NETKEY_OLD_FILE_AID;
	  static const char *CFG_APPLET_SO_PIN;
	  static const char *CFG_DEBUG_ENABLE;
	  static const char *CFG_DEBUG_FILENAME;
          static const char *CFG_DEBUG_LEVEL;
          static const char *CFG_ERROR_LEVEL;
	  static const char *CFG_ERROR_ENABLE;
	  static const char *CFG_ERROR_FILENAME;
	  static const char *CFG_SELFTEST_LEVEL;
	  static const char *CFG_SELFTEST_ENABLE;
	  static const char *CFG_SELFTEST_FILENAME;
	  static const char *CFG_CHANNEL_SEC_LEVEL;
	  static const char *CFG_CHANNEL_ENCRYPTION;
          static const char *CFG_DEBUG_FILE_TYPE;
          static const char *CFG_ERROR_FILE_TYPE;
          static const char *CFG_SELFTEST_FILE_TYPE;
          static const char *CFG_DEBUG_PREFIX;
          static const char *CFG_ERROR_PREFIX;
          static const char *CFG_SELFTEST_PREFIX;


      static const char *CFG_AUTHS_ENABLE;
      static const char *CFG_AUTHS_CURRENTIMPL;
      static const char *CFG_AUTHS_PLUGINS_NUM;
      static const char *CFG_AUTHS_PLUGIN_NAME;

      static const char *CFG_IPUBLISHER_LIB;
      static const char *CFG_IPUBLISHER_FACTORY;
      static const char *CFG_TOKENDB_ALLOWED_TRANSITIONS;
      static const char *CFG_OPERATIONS_ALLOWED_TRANSITIONS;

  public:
	  static const char *TKS_RESPONSE_STATUS;
	  static const char *TKS_RESPONSE_SessionKey;
	  static const char *TKS_RESPONSE_EncSessionKey;
	  static const char *TKS_RESPONSE_KEK_DesKey;
	  static const char *TKS_RESPONSE_DRM_Trans_DesKey;
	  static const char *TKS_RESPONSE_HostCryptogram;

  public:
          static int m_used_tks_conn;
          static int m_used_ca_conn;

          static int m_used_drm_conn;
          static HttpConnection* m_drmConnection[];
          static int m_drmConns_len;
          static int m_pod_curr;
          static bool m_pod_enable;
          static PRLock *m_verify_lock;
          static PRLock *m_pod_lock;
          static PRLock *m_error_log_lock;
          static PRLock *m_debug_log_lock;
          static int m_debug_log_level;
          static int m_error_log_level;
          static PRThread *m_flush_thread;
          static size_t m_bytes_unflushed;
          static size_t m_buffer_size;
          static int m_flush_interval;

      static HttpConnection* m_caConnection[];
      static HttpConnection* m_tksConnection[];
      static int m_caConns_len;
      static int m_tksConns_len;
      static void SetCurrentIndex(HttpConnection *&conn, int index);

          static RA_Context *m_ctx;

        static int Failover(HttpConnection *&conn, int len);   

          static bool isAlgorithmECC(BYTE algorithm);
};

#endif /* RA_H */
