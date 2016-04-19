/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.set.assistant.evaluator.impl.payload;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;

import org.jboss.set.aphrodite.Aphrodite;
import org.jboss.set.aphrodite.config.TrackerType;
import org.jboss.set.aphrodite.domain.Comment;
import org.jboss.set.aphrodite.domain.CommitStatus;
import org.jboss.set.aphrodite.domain.Issue;
import org.jboss.set.aphrodite.domain.Patch;
import org.jboss.set.aphrodite.spi.NotFoundException;
import org.jboss.set.assistant.Constants;
import org.jboss.set.assistant.data.payload.AssociatedPullRequest;
import org.jboss.set.assistant.evaluator.PayloadEvaluator;
import org.jboss.set.assistant.evaluator.PayloadEvaluatorContext;

/**
 * @author wangc
 *
 */
public class AssociatedPullRequestEvaluator implements PayloadEvaluator {

    private static final Logger logger = Logger.getLogger(AssociatedPullRequestEvaluator.class.getCanonicalName());

    public static final String KEY = "associatedPullRequest";

    @Override
    public String name() {
        return "Associate PullRequest Evaluator";
    }

    @Override
    public void eval(PayloadEvaluatorContext context, Map<String, Object> data) {
        Issue dependencyIssue = context.getIssue();
        Aphrodite aphrodite = context.getAphrodite();
        TrackerType trackerType = context.getTrackerType();

        List<URL> relatedPullRequestsURL = new ArrayList<>();

        if (trackerType.equals(TrackerType.BUGZILLA)) {
            // scan comments to get relevant pull request;
            List<Comment> comments = dependencyIssue.getComments();
            comments.stream().forEach(e -> {
                Matcher matcher = Constants.RELATED_PR_PATTERN.matcher(e.getBody());
                while (matcher.find()) {
                    if (matcher.groupCount() == 3) {
                        URL relatedPullRequestURL;
                        try {
                            relatedPullRequestURL = new URL("https://github.com/" + matcher.group(1) + "/" + matcher.group(2)
                                    + "/pull/" + matcher.group(3));
                            relatedPullRequestsURL.add(relatedPullRequestURL);
                        } catch (Exception e1) {
                            e1.printStackTrace();
                        }
                    }
                }
            });
        } else {
            // TODO based on pull request field value on JIRA
        }

        List<AssociatedPullRequest> dataList = new ArrayList<>();
        for (URL url : relatedPullRequestsURL) {
            Patch p = null;
            try {
                p = aphrodite.getPatch(url);
            } catch (NotFoundException e) {
                logger.log(Level.WARNING, "Unable to find related Pull Request for issue: " + dependencyIssue.getURL(), e);
            }
            Optional<CommitStatus> commitStatus = Optional.of(CommitStatus.UNKNOWN);
            try {
                commitStatus = Optional.of(aphrodite.getCommitStatusFromPatch(p));
            } catch (NotFoundException e) {
                logger.log(Level.FINE, "Unable to find build result for pull request : " + url, e);
            }
            dataList.add(new AssociatedPullRequest(p.getId(), p.getURL(), p.getCodebase().getName(), commitStatus.orElse(CommitStatus.UNKNOWN).toString()));
        }
        data.put(KEY, dataList);

    }
}
