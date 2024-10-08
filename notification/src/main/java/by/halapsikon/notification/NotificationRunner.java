package by.halapsikon.notification;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sns.model.ListTopicsResult;
import com.amazonaws.services.sns.model.Topic;

import java.util.Optional;
import java.util.stream.Collectors;

public class NotificationRunner implements RequestHandler<SQSEvent, String> {

    private final static String EMAIL_HEADER = "New images were uploaded via SAM2!\n\nSee the next ones:\n";

    @Override
    public String handleRequest(SQSEvent sqsEvent, Context context) {
        var logger = context.getLogger();

        logger.log("Start function\n");
        var emailMessage = retrieveEmailMessage(sqsEvent, logger);

        var snsClient = buildSnsClient();

        var topicARN = retrieveSNSTopicARN(snsClient);

        snsClient.publish(topicARN, emailMessage);

        logger.log("End function\n");

        return emailMessage;
    }

    private static String retrieveEmailMessage(SQSEvent sqsEvent, LambdaLogger logger) {
        var imageMessages = sqsEvent.getRecords().stream()
                .map(SQSMessage::getBody)
                .collect(Collectors.joining());
        logger.log(imageMessages);

        return String.join("", EMAIL_HEADER, imageMessages);
    }

    private static AmazonSNS buildSnsClient() {
        var region = System.getenv("AWS_REGION");
        return AmazonSNSClient.builder()
                .withRegion(region)
                .build();
    }

    private String retrieveSNSTopicARN(AmazonSNS amazonSNS) {
        String nextToken = null;
        Optional<String> topicArn;
        do {
            ListTopicsResult result = amazonSNS.listTopics(nextToken);

            topicArn = result.getTopics().stream()
                    .map(Topic::getTopicArn)
                    .filter(s -> s.endsWith(System.getenv("SNS_TOPIC_NAME")))
                    .findFirst();

            nextToken = result.getNextToken();
        } while (nextToken != null && topicArn.isEmpty());
        return topicArn.get();
    }
}
