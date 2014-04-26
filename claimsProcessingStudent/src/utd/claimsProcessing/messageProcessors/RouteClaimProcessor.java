package utd.claimsProcessing.messageProcessors;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Queue;
import javax.jms.Session;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import utd.claimsProcessing.domain.Claim;
import utd.claimsProcessing.domain.ClaimFolder;
import utd.claimsProcessing.domain.ProcedureCategory;
import utd.claimsProcessing.domain.RejectedClaimInfo;

/**
 * A message processor responsible for retrieving the Member identified by the Claim
 * from the MemberDAO. The retrieved member is attached to the ClaimFolder before
 * passing to the next step in the process. 
 */

public class RouteClaimProcessor extends MessageProcessor implements MessageListener
{
	private final static Logger logger = Logger.getLogger(RetrieveMemberProcessor.class);
	
	private MessageProducer dentalProducer;
	private MessageProducer GPProducer;
	private MessageProducer optometryProducer;
	private MessageProducer radiologyProducer;

	
	public RouteClaimProcessor(Session session)
	{
		super(session);
	}

	public void initialize() throws JMSException
	{
		Queue dentalQueue = getSession().createQueue(QueueNames.processDentalClaim);
		Queue GPQueue = getSession().createQueue(QueueNames.processGPClaim);
		Queue optometryQueue = getSession().createQueue(QueueNames.processOptometryClaim);
		Queue radiologyQueue = getSession().createQueue(QueueNames.processRadiologyClaim);

		dentalProducer = getSession().createProducer(dentalQueue);
		GPProducer = getSession().createProducer(GPQueue);
		optometryProducer = getSession().createProducer(optometryQueue);
		radiologyProducer = getSession().createProducer(radiologyQueue);

	}

	public void onMessage(Message message)
	{
		logger.debug("RouteClaimProcessor ReceivedMessage");

		try {
			Object object = ((ObjectMessage) message).getObject();
			ClaimFolder claimFolder = (ClaimFolder)object;
			
			ProcedureCategory procedureCategory = claimFolder.getProcedure().getProcedureCategory();
			
			if(procedureCategory == ProcedureCategory.Dental) {
				logger.debug("Found ProcedureCategory: Dental" );

				Message claimMessage = getSession().createObjectMessage(claimFolder);
				dentalProducer.send(claimMessage);
				logger.debug("Finished Sending: ProcedureCategory: Dental");
			}
			else if(procedureCategory == ProcedureCategory.GeneralPractice) {
				logger.debug("Found ProcedureCategory: GeneralPractice" );

				Message claimMessage = getSession().createObjectMessage(claimFolder);
				GPProducer.send(claimMessage);
				logger.debug("Finished Sending: ProcedureCategory: GeneralPractice");
				}
			else if(procedureCategory == ProcedureCategory.Optometry) {
				logger.debug("Found ProcedureCategory: Optometry" );

				Message claimMessage = getSession().createObjectMessage(claimFolder);
				optometryProducer.send(claimMessage);
				logger.debug("Finished Sending: ProcedureCategory: Optometry");
				}
			else if(procedureCategory == ProcedureCategory.Radiology) {
				logger.debug("Found ProcedureCategory: Radiology" );

				Message claimMessage = getSession().createObjectMessage(claimFolder);
				radiologyProducer.send(claimMessage);
				logger.debug("Finished Sending: ProcedureCategory: Radiology");
				}
			else{
				Claim claim = claimFolder.getClaim();
				RejectedClaimInfo rejectedClaimInfo = new RejectedClaimInfo("Invalid Procedure Category");
				claimFolder.setRejectedClaimInfo(rejectedClaimInfo);
				if(!StringUtils.isBlank(claim.getReplyTo())) {
					rejectedClaimInfo.setEmailAddr(claim.getReplyTo());
				}
				rejectClaim(claimFolder);
			}
		}
		catch (Exception ex) {
			logError("RouteClaimProcessor.onMessage() " + ex.getMessage(), ex);
		}
	}
}
