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

import utd.claimsProcessing.dao.MemberDAO;
import utd.claimsProcessing.dao.PolicyDAO;
import utd.claimsProcessing.dao.ProviderDAO;
import utd.claimsProcessing.domain.Claim;
import utd.claimsProcessing.domain.ClaimFolder;
import utd.claimsProcessing.domain.Member;
import utd.claimsProcessing.domain.Policy;
import utd.claimsProcessing.domain.PolicyState;
import utd.claimsProcessing.domain.Provider;
import utd.claimsProcessing.domain.RejectedClaimInfo;

/**
 * A message processor responsible for retrieving the Policy identified by the Claim
 * from the PolicyrDAO. The retrieved member is attached to the ClaimFolder before
 * passing to the next step in the process. 
 */
public class RetrievePolicyProcessor extends MessageProcessor implements MessageListener{

	private final static Logger logger = Logger.getLogger(RetrieveMemberProcessor.class);
	
	private MessageProducer producer;
	
	public RetrievePolicyProcessor(Session session)
	{
		super(session);
	}

	public void initialize() throws JMSException
	{
		Queue queue = getSession().createQueue(QueueNames.retrieveProcedure);
		producer = getSession().createProducer(queue);
	}

	@Override
	public void onMessage(Message message) {
		logger.debug("RetrievePolicyProcessor ReceivedMessage");
		
		try{
			//get the claimFolder object from the message
			Object object = ((ObjectMessage) message).getObject();
			ClaimFolder claimFolder = (ClaimFolder)object;
			
			String policyID = claimFolder.getMember().getPolicyID();
			Policy policy = PolicyDAO.getSingleton().retrievePolicy(policyID);
			
			if(policy == null) {
				Claim claim = claimFolder.getClaim();
				RejectedClaimInfo rejectedClaimInfo = new RejectedClaimInfo("Policy not found: " + policyID);
				claimFolder.setRejectedClaimInfo(rejectedClaimInfo);
				if(!StringUtils.isBlank(claim.getReplyTo())) {
					rejectedClaimInfo.setEmailAddr(claim.getReplyTo());
				}
				rejectClaim(claimFolder);
			}
			else if(policy.getPolicyState() == PolicyState.expired){
				Claim claim = claimFolder.getClaim();
				RejectedClaimInfo rejectedClaimInfo = new RejectedClaimInfo("Member Policy has Expired");
				claimFolder.setRejectedClaimInfo(rejectedClaimInfo);
				if(!StringUtils.isBlank(claim.getReplyTo())) {
					rejectedClaimInfo.setEmailAddr(claim.getReplyTo());
				}
				rejectClaim(claimFolder);
			}
			else if(policy.getPolicyState() == PolicyState.suspended){
				Claim claim = claimFolder.getClaim();
				RejectedClaimInfo rejectedClaimInfo = new RejectedClaimInfo("Member Policy is suspended");
				claimFolder.setRejectedClaimInfo(rejectedClaimInfo);
				if(!StringUtils.isBlank(claim.getReplyTo())) {
					rejectedClaimInfo.setEmailAddr(claim.getReplyTo());
				}
				rejectClaim(claimFolder);
			}
			else {
				logger.debug("Found Policy: " + policyID + "With PolicyState: " + policy.getPolicyState() );

				claimFolder.setPolicy(policy);
			
				Message claimMessage = getSession().createObjectMessage(claimFolder);
				producer.send(claimMessage);
				logger.debug("Finished Sending: " + policyID + "With PolicyState: " + policy.getPolicyState());
			}
		}
		catch (Exception ex) {
			logError("RetrieveMemberProcessor.onMessage() " + ex.getMessage(), ex);
		}
	}//end onMessage
}
