/*
 *
 *
 * Copyright (C) 2009 Nortel, certain elements licensed under a Contributor Agreement.
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the LGPL license.
 *
 *
 */
package org.sipfoundry.sipxconfig.openfire;

import java.util.Collection;
import java.util.List;

import org.dom4j.Document;
import org.dom4j.Element;
import org.sipfoundry.sipxconfig.admin.dialplan.config.XmlFile;
import org.sipfoundry.sipxconfig.common.Closure;
import org.sipfoundry.sipxconfig.common.CoreContext;
import org.sipfoundry.sipxconfig.common.User;
import org.sipfoundry.sipxconfig.conference.Conference;
import org.sipfoundry.sipxconfig.conference.ConferenceBridgeContext;
import org.sipfoundry.sipxconfig.im.ImAccount;
import org.sipfoundry.sipxconfig.setting.Group;
import org.sipfoundry.sipxconfig.setting.type.BooleanSetting;
import org.springframework.beans.factory.annotation.Required;

import static org.apache.commons.lang.StringUtils.defaultString;
import static org.sipfoundry.sipxconfig.common.DaoUtils.forAllUsersDo;

public class XmppAccountInfo extends XmlFile {
    private static final String NAMESPACE = "http://www.sipfoundry.org/sipX/schema/xml/xmpp-account-info-00-00";
    private static final String MUC_SUBDOMAIN = "conference";
    private static final String USER = "user";
    private static final String USER_NAME = "user-name";
    private static final String PASSWORD = "password";
    private static final String DESCRIPTION = "description";
    private CoreContext m_coreContext;
    private ConferenceBridgeContext m_conferenceContext;

    @Required
    public void setCoreContext(CoreContext coreContext) {
        m_coreContext = coreContext;
    }

    @Required
    public void setConferenceBridgeContext(ConferenceBridgeContext conferenceContext) {
        m_conferenceContext = conferenceContext;
    }

    @Override
    public Document getDocument() {
        Document document = FACTORY.createDocument();
        final Element accountInfos = document.addElement("xmpp-account-info", NAMESPACE);

        Closure<User> closure = new Closure<User>() {
            @Override
            public void execute(User user) {
                createUserAccount(user, accountInfos);
            }
        };
        forAllUsersDo(m_coreContext, closure);

        List<Group> groups = m_coreContext.getGroups();
        for (Group group : groups) {
            createXmmpGroup(group, accountInfos);
        }

        List<Conference> conferences = m_conferenceContext.getAllConferences();
        for (Conference conf : conferences) {
            createXmppChatRoom(conf, accountInfos);
        }
        return document;
    }

    private void createUserAccount(User user, Element accountInfos) {
        ImAccount imAccount = new ImAccount(user);
        if (!imAccount.isEnabled()) {
            return;
        }

        Element userAccounts = accountInfos.addElement(USER);
        userAccounts.addElement(USER_NAME).setText(imAccount.getImId());
        userAccounts.addElement("sip-user-name").setText(user.getName());
        userAccounts.addElement("display-name").setText(imAccount.getImDisplayName());
        userAccounts.addElement(PASSWORD).setText(imAccount.getImPassword());
        userAccounts.addElement("on-the-phone-message").setText(imAccount.getOnThePhoneMessage());
        userAccounts.addElement("advertise-on-call-status").setText(
                Boolean.toString(imAccount.advertiseSipPresence()));
        userAccounts.addElement("show-on-call-details").setText(Boolean.toString(imAccount.includeCallInfo()));
    }

    private void createXmppChatRoom(Conference conference, Element accountInfos) {
        if (!conference.isEnabled()) {
            return;
        }
        User owner = conference.getOwner();
        if (owner == null) {
            return;
        }
        ImAccount imAccount = new ImAccount(owner);
        if (!imAccount.isEnabled()) {
            return;
        }

        Element chatRoom = accountInfos.addElement("chat-room");

        String displayName = conference.getName();

        chatRoom.addElement("subdomain").setText(MUC_SUBDOMAIN);
        chatRoom.addElement("room-owner").setText(imAccount.getImId());
        chatRoom.addElement("room-name").setText(displayName);
        chatRoom.addElement(DESCRIPTION).setText(defaultString(conference.getDescription()));
        chatRoom.addElement(PASSWORD).setText(defaultString(conference.getParticipantAccessCode()));
        addBooleanSetting(chatRoom, conference, "moderated", "chat-meeting/moderated");
        addBooleanSetting(chatRoom, conference, "is-public-room", "chat-meeting/public");
        addBooleanSetting(chatRoom, conference, "is-members-only", "chat-meeting/members-only");
        addBooleanSetting(chatRoom, conference, "is-persistent", "chat-meeting/persistent");
        addBooleanSetting(chatRoom, conference, "log-room-conversations", "chat-meeting/log-conversations");
        chatRoom.addElement("conference-extension").setText(conference.getExtension());
    }

    private void addBooleanSetting(Element chatRoom, Conference conference, String elementName, String settingName) {
        Boolean value = (Boolean) conference.getSettingTypedValue(settingName);
        chatRoom.addElement(elementName).setText(value.toString());
    }

    private void createXmmpGroup(Group group, Element accountInfos) {
        // HACK: we assume that 'replicate-group' is a standard boolean setting
        Boolean replicate = (Boolean) group.getSettingTypedValue(new BooleanSetting(), "im/im-account");
        if (replicate == null || !replicate) {
            return;
        }
        Element xmmpGroup = accountInfos.addElement("group");
        xmmpGroup.addElement("group-name").setText(group.getName());
        String groupDescription = group.getDescription();
        if (groupDescription != null) {
            xmmpGroup.addElement(DESCRIPTION).setText(groupDescription);
        }
        Collection<User> groupMembers = m_coreContext.getGroupMembers(group);
        if (groupMembers != null && groupMembers.size() > 0) {
            for (User user : groupMembers) {
                if (user.getImId() != null) {
                    Element userElement = xmmpGroup.addElement(USER);
                    userElement.addElement(USER_NAME).setText(user.getImId());
                }
            }
        }
    }
}