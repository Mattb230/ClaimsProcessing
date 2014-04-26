package utd.claimsProcessing.messageProcessors;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Queue;
import javax.jms.Session;

import org.apache.log4j.Logger;

import utd.claimsProcessing.domain.ClaimFolder;
import utd.claimsProcessing.domain.PolicyState;
import utd.claimsProcessing.domain.RejectedClaimInfo;



public class ProcessRadiologyClaimProcessor extends MessageProcessor implements MessageListener
{
	private final static Logger logger = Logger.getLogger(RetrieveMemberProcessor.class);
	
	private MessageProducer denyClaimProducer;
	private MessageProducer payClaimProducer;
	
	public ProcessRadiologyClaimProcessor(Session session)
	{
		super(session);
	}

	public void initialize() throws JMSException
	{
		Queue denyClaimQueue = getSession().createQueue(QueueNames.denyClaim);
		denyClaimProducer = getSession().createProducer(denyClaimQueue);
		Queue payClaimQueue = getSession().createQueue(QueueNames.payClaim);
		payClaimProducer = getSession().createProducer(payClaimQueue);
	}

	
	public void onMessage(Message message)
	{
		logger.debug("ProcessRadiologyClaimProcessor ReceivedMessage");

		try {
			Object object = ((ObjectMessage) message).getObject();
			ClaimFolder claimFolder = (ClaimFolder)object;
			//if Policy is active and precedure is covered under Policy
			if(claimFolder.validatePolicy() && claimFolder.validateProcedure(claimFolder.getProcedure().getProcedureCategory())) {
				
				logger.debug("Validated Policy and Procedure");
			
				Message claimMessage = getSession().createObjectMessage(claimFolder);
				payClaimProducer.send(claimMessage);
				logger.debug("Finished Sending: Policy and Procedure Approval");
				
			}
			else{
				//if policy is good but procedure category isnt covered under policy
				if(claimFolder.validatePolicy() && !claimFolder.validateProcedure(claimFolder.getProcedure().getProcedureCategory())){
					logger.debug("Could Not Validate Procedure");
					RejectedClaimInfo rejectedClaimInfo = new RejectedClaimInfo("Procedure Category is not Covered by Policy");
					claimFolder.setRejectedClaimInfo(rejectedClaimInfo);
				}
				//if policy is invalid but procedure category is covered under policy
				else if(!claimFolder.validatePolicy() && claimFolder.validateProcedure(claimFolder.getProcedure().getProcedureCategory())){
					logger.debug("Could Not Validate Policy");
					if(claimFolder.getPolicy().getPolicyState() == PolicyState.suspended){
						RejectedClaimInfo rejectedClaimInfo = new RejectedClaimInfo("Member Policy is Suspended");
						claimFolder.setRejectedClaimInfo(rejectedClaimInfo);
					}
					else{
						RejectedClaimInfo rejectedClaimInfo = new RejectedClaimInfo("Member Policy is Expired");
						claimFolder.setRejectedClaimInfo(rejectedClaimInfo);
					}
				}
				//policy invalid and category not covered under policy
				else{
					logger.debug("Could Not Validate Procedure");
					String rejectionInfo;
					if(claimFolder.getPolicy().getPolicyState() == PolicyState.suspended){
						rejectionInfo = "Member Policy is Suspended";
					}
					else{
						rejectionInfo = "Member Policy is Expired";}
					rejectionInfo += " and Procedure Category is not Covered by Policy";
					RejectedClaimInfo rejectedClaimInfo = new RejectedClaimInfo(rejectionInfo);
					claimFolder.setRejectedClaimInfo(rejectedClaimInfo);
				}
				
				Message claimMessage = getSession().createObjectMessage(claimFolder);
				denyClaimProducer.send(claimMessage);
				logger.debug("Finished Sending: Policy and Procedure Denial");
			}

		}
		catch (Exception ex) {
			logError("ProcessRadiologyClaimProcessor.onMessage() " + ex.getMessage(), ex);
		}
	}
}

