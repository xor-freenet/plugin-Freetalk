/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui.web;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;

import plugins.Freetalk.FTOwnIdentity;
import plugins.Freetalk.Freetalk;
import plugins.Freetalk.Message;
import plugins.Freetalk.WoT.WoTIdentity;
import plugins.Freetalk.WoT.WoTIdentityManager;
import plugins.Freetalk.WoT.WoTOwnIdentity;
import plugins.Freetalk.WoT.WoTIdentityManager.IntroductionPuzzle;
import plugins.Freetalk.exceptions.NoSuchBoardException;
import plugins.Freetalk.exceptions.NoSuchIdentityException;
import plugins.Freetalk.exceptions.NoSuchMessageException;
import plugins.Freetalk.exceptions.NotTrustedException;
import freenet.client.HighLevelSimpleClient;
import freenet.clients.http.PageMaker;
import freenet.clients.http.RedirectException;
import freenet.clients.http.Toadlet;
import freenet.clients.http.ToadletContainer;
import freenet.clients.http.ToadletContext;
import freenet.clients.http.ToadletContextClosedException;
import freenet.clients.http.filter.ContentFilter;
import freenet.clients.http.filter.ContentFilter.FilterOutput;
import freenet.node.NodeClientCore;
import freenet.support.api.Bucket;
import freenet.support.api.HTTPRequest;
import freenet.support.io.Closer;
import freenet.support.io.TempBucketFactory;


/**
 * 
 * @author xor (xor@freenetproject.org)
 * @author saces
 */
public class WebInterface {
	
	private final Freetalk mFreetalk;
	
	protected final PageMaker mPageMaker;
	
	private FTOwnIdentity mOwnIdentity;
	
	// Visible
	private final WebInterfaceToadlet homeToadlet;
	private final WebInterfaceToadlet messagesToadlet;
	private final WebInterfaceToadlet identitiesToadlet;
	private final WebInterfaceToadlet logOutToadlet;
	
	// Invisible
	private final WebInterfaceToadlet logInToadlet;
	private final WebInterfaceToadlet createIdentityToadlet;
	private final WebInterfaceToadlet newThreadToadlet;
	private final WebInterfaceToadlet showBoardToadlet;
	private final WebInterfaceToadlet showThreadToadlet;
	private final WebInterfaceToadlet newReplyToadlet;
	private final WebInterfaceToadlet newBoardToadlet;
	private final WebInterfaceToadlet changeTrustToadlet;
	private final WebInterfaceToadlet getPuzzleToadlet;
	
	class HomeWebInterfaceToadlet extends WebInterfaceToadlet {

		protected HomeWebInterfaceToadlet(HighLevelSimpleClient client, WebInterface wi, NodeClientCore core, String pageTitle) {
			super(client, wi, core, pageTitle);
		}

		@Override
		WebPage makeWebPage(HTTPRequest req, ToadletContext context) {
			if(!mFreetalk.wotConnected())
				return new WoTIsMissingPage(webInterface, req, mFreetalk.wotOutdated());
			return new Welcome(webInterface, getLoggedInOwnIdentity(), req);
		}

		@Override
		public boolean isEnabled(ToadletContext ctx) {
			return super.isEnabled(ctx) && webInterface.getLoggedInOwnIdentity() != null;
		}

	}
	
	class MessagesWebInterfaceToadlet extends WebInterfaceToadlet {

		protected MessagesWebInterfaceToadlet(HighLevelSimpleClient client, WebInterface wi, NodeClientCore core, String pageTitle) {
			super(client, wi, core, pageTitle);
		}

		@Override
		WebPage makeWebPage(HTTPRequest req, ToadletContext context) {
			if(!mFreetalk.wotConnected())
				return new WoTIsMissingPage(webInterface, req, mFreetalk.wotOutdated());
			return new BoardsPage(webInterface, getLoggedInOwnIdentity(), req);
		}
		
		@Override
		public boolean isEnabled(ToadletContext ctx) {
			return super.isEnabled(ctx) && webInterface.getLoggedInOwnIdentity() != null;
		}
		
	}
	
	class IdentitiesWebInterfaceToadlet extends WebInterfaceToadlet {

		protected IdentitiesWebInterfaceToadlet(HighLevelSimpleClient client, WebInterface wi, NodeClientCore core, String pageTitle) {
			super(client, wi, core, pageTitle);
		}

		@Override
		WebPage makeWebPage(HTTPRequest req, ToadletContext context) {
			if(!mFreetalk.wotConnected())
				return new WoTIsMissingPage(webInterface, req, mFreetalk.wotOutdated());
			return new IdentityEditor(webInterface, getLoggedInOwnIdentity(), req);
		}
		
		@Override
		public boolean isEnabled(ToadletContext ctx) {
			return super.isEnabled(ctx) && webInterface.getLoggedInOwnIdentity() != null;
		}
		
	}
	
	protected final URI logIn;
	
	class LogOutWebInterfaceToadlet extends WebInterfaceToadlet {

		protected LogOutWebInterfaceToadlet(HighLevelSimpleClient client, WebInterface wi, NodeClientCore core, String pageTitle) {
			super(client, wi, core, pageTitle);
		}

		@Override
		WebPage makeWebPage(HTTPRequest req, ToadletContext context) throws RedirectException {
			if(!mFreetalk.wotConnected())
				return new WoTIsMissingPage(webInterface, req, mFreetalk.wotOutdated());
			setLoggedInOwnIdentity(null);
			throw new RedirectException(logIn);
		}
		
		@Override
		public boolean isEnabled(ToadletContext ctx) {
			return super.isEnabled(ctx) && webInterface.getLoggedInOwnIdentity() != null;
		}
		
	}
	
	public class LogInWebInterfaceToadlet extends WebInterfaceToadlet {

		protected LogInWebInterfaceToadlet(HighLevelSimpleClient client, WebInterface wi, NodeClientCore core, String pageTitle) {
			super(client, wi, core, pageTitle);
		}

		/** Log an user in from a POST and redirect to the BoardsPage */
		@Override
		public void handleMethodPOST(URI uri, HTTPRequest request, ToadletContext ctx) throws ToadletContextClosedException, IOException, RedirectException {
			String pass = request.getPartAsString("formPassword", 32);
			if ((pass.length() == 0) || !pass.equals(core.formPassword)) {
				writeHTMLReply(ctx, 403, "Forbidden", "Invalid form password.");
				return;
			}

			try {
				setLoggedInOwnIdentity(mFreetalk.getIdentityManager().getOwnIdentity(request.getPartAsString("OwnIdentityID", 64)));
			} catch(NoSuchIdentityException e) {
				throw new RedirectException(logIn);
			}

			writeTemporaryRedirect(ctx, "Login successful, redirecting to the board overview", Freetalk.PLUGIN_URI + "/messages");
		}

		@Override
		WebPage makeWebPage(HTTPRequest req, ToadletContext context) {
			if(!mFreetalk.wotConnected())
				return new WoTIsMissingPage(webInterface, req, mFreetalk.wotOutdated());
			try {
				setLoggedInOwnIdentity(mFreetalk.getIdentityManager().getOwnIdentity(req.getParam("OwnIdentityID")));
				return new Welcome(webInterface, getLoggedInOwnIdentity(), req);
			}
			catch(NoSuchIdentityException e) {
				/* Ignore and continue as if the user did not specify an identity, he will end up with a LogInPage */
			}
			return new LogInPage(webInterface, getLoggedInOwnIdentity(), req);
		}
		
		@Override
		public Toadlet showAsToadlet() {
			return homeToadlet;
		}
		
		@Override
		public boolean isEnabled(ToadletContext ctx) {
			return super.isEnabled(ctx) && webInterface.getLoggedInOwnIdentity() == null;
		}
		
	}

	public class ChangeTrustWebInterfaceToadlet extends WebInterfaceToadlet {

		protected ChangeTrustWebInterfaceToadlet(HighLevelSimpleClient client, WebInterface wi, NodeClientCore core, String pageTitle) {
			super(client, wi, core, pageTitle);
		}

		@Override
		public void handleMethodPOST(URI uri, HTTPRequest request, ToadletContext ctx) throws ToadletContextClosedException, IOException, RedirectException {
			String pass = request.getPartAsString("formPassword", 32);
			if ((pass.length() == 0) || !pass.equals(core.formPassword)) {
				writeHTMLReply(ctx, 403, "Forbidden", "Invalid form password.");
				return;
			}
			try {
				// TODO: These casts are ugly.
				WoTOwnIdentity own = (WoTOwnIdentity)mFreetalk.getIdentityManager().getOwnIdentity(request.getPartAsString("OwnIdentityID", 64));
				WoTIdentity other = (WoTIdentity)mFreetalk.getIdentityManager().getIdentity(request.getPartAsString("OtherIdentityID", 64));
				int change = Integer.parseInt(request.getPartAsString("TrustChange", 5));
				
				int trust;
				try {
					trust = own.getTrustIn(other);
				} catch (NotTrustedException e) {
					trust = 0;
				}
				own.setTrust(other, trust+change, "Freetalk web interface");

				try {
					Message message = mFreetalk.getMessageManager().get(request.getPartAsString("MessageID", 128));
					own.setAssessed(message, true);
					own.storeAndCommit();
				} catch (NoSuchMessageException e) {
				}
			} catch(Exception e) {
				// FIXME: provide error message
			}

			writeTemporaryRedirect(ctx, "Changing trust succesful, redirecting to thread", Freetalk.PLUGIN_URI + "/showThread?board=" + request.getPartAsString("BoardName", 64) + "&id=" + request.getPartAsString("ThreadID", 128));
		}

		@Override
		WebPage makeWebPage(HTTPRequest req, ToadletContext context) {
			// not expected to make it here
			return new Welcome(webInterface, getLoggedInOwnIdentity(), req);
		}
		
		@Override
		public Toadlet showAsToadlet() {
			return homeToadlet;
		}
	}
	
	class CreateIdentityWebInterfaceToadlet extends WebInterfaceToadlet {

		protected CreateIdentityWebInterfaceToadlet(HighLevelSimpleClient client, WebInterface wi, NodeClientCore core, String pageTitle) {
			super(client, wi, core, pageTitle);
		}

		@Override
		WebPage makeWebPage(HTTPRequest req, ToadletContext context) {
			if(!mFreetalk.wotConnected())
				return new WoTIsMissingPage(webInterface, req, mFreetalk.wotOutdated());
			return new CreateIdentityWizard(webInterface, req);
		}
		
		@Override
		public Toadlet showAsToadlet() {
			return identitiesToadlet;
		}
		
	}
	
	class NewThreadWebInterfaceToadlet extends WebInterfaceToadlet {

		protected NewThreadWebInterfaceToadlet(HighLevelSimpleClient client, WebInterface wi, NodeClientCore core, String pageTitle) {
			super(client, wi, core, pageTitle);
		}

		@Override
		WebPage makeWebPage(HTTPRequest req, ToadletContext context) throws RedirectException {
			if(!mFreetalk.wotConnected())
				return new WoTIsMissingPage(webInterface, req, mFreetalk.wotOutdated());
			if(getLoggedInOwnIdentity() == null)
				throw new RedirectException(logIn);
			try {
				return new NewThreadPage(webInterface, getLoggedInOwnIdentity(), req);
			} catch (NoSuchBoardException e) {
				return new ErrorPage(webInterface, getLoggedInOwnIdentity(), req, "Unknown board "+req.getParam("name"), "Unknown board "+req.getParam("name"));
				
			}
		}
		
		@Override
		public Toadlet showAsToadlet() {
			return messagesToadlet;
		}
		
		@Override
		public boolean isEnabled(ToadletContext ctx) {
			return super.isEnabled(ctx) && webInterface.getLoggedInOwnIdentity() != null;
		}
		
	}
	
	class ShowBoardWebInterfaceToadlet extends WebInterfaceToadlet {

		protected ShowBoardWebInterfaceToadlet(HighLevelSimpleClient client, WebInterface wi, NodeClientCore core, String pageTitle) {
			super(client, wi, core, pageTitle);
		}

		@Override
		WebPage makeWebPage(HTTPRequest req, ToadletContext context) throws RedirectException {
			if(!mFreetalk.wotConnected())
				return new WoTIsMissingPage(webInterface, req, mFreetalk.wotOutdated());
			if(getLoggedInOwnIdentity() == null)
				throw new RedirectException(logIn);
			try {
				return new BoardPage(webInterface, getLoggedInOwnIdentity(), req);
			} catch (NoSuchBoardException e) {
				return new ErrorPage(webInterface, getLoggedInOwnIdentity(), req, "Unknown board "+req.getParam("name"), "Unknown board "+req.getParam("name"));
				
			}
		}
		
		@Override
		public Toadlet showAsToadlet() {
			return messagesToadlet;
		}
		
		@Override
		public boolean isEnabled(ToadletContext ctx) {
			return super.isEnabled(ctx) && webInterface.getLoggedInOwnIdentity() != null;
		}
		
	}
	
	class ShowThreadWebInterfaceToadlet extends WebInterfaceToadlet {

		protected ShowThreadWebInterfaceToadlet(HighLevelSimpleClient client, WebInterface wi, NodeClientCore core, String pageTitle) {
			super(client, wi, core, pageTitle);
		}

		@Override
		WebPage makeWebPage(HTTPRequest req, ToadletContext context) throws RedirectException {
			if(!mFreetalk.wotConnected())
				return new WoTIsMissingPage(webInterface, req, mFreetalk.wotOutdated());
			if(getLoggedInOwnIdentity() == null)
				throw new RedirectException(logIn);
			try {
				return new ThreadPage(webInterface, getLoggedInOwnIdentity(), req);
			} catch (NoSuchBoardException e) {
				return new ErrorPage(webInterface, getLoggedInOwnIdentity(), req, "Unknown board "+req.getParam("name"), "Unknown board "+req.getParam("name"));
			} catch (NoSuchMessageException e) {
				return new ErrorPage(webInterface, getLoggedInOwnIdentity(), req, "Unknown message "+req.getParam("id"), "Unknown message "+req.getParam("id"));
			}
		}
		
		@Override
		public Toadlet showAsToadlet() {
			return messagesToadlet;
		}
		
		@Override
		public boolean isEnabled(ToadletContext ctx) {
			return super.isEnabled(ctx) && webInterface.getLoggedInOwnIdentity() != null;
		}
		
	}
	
	class NewReplyWebInterfaceToadlet extends WebInterfaceToadlet {

		protected NewReplyWebInterfaceToadlet(HighLevelSimpleClient client, WebInterface wi, NodeClientCore core, String pageTitle) {
			super(client, wi, core, pageTitle);
		}

		@Override
		WebPage makeWebPage(HTTPRequest req, ToadletContext context) throws RedirectException {
			if(!mFreetalk.wotConnected())
				return new WoTIsMissingPage(webInterface, req, mFreetalk.wotOutdated());
			if(getLoggedInOwnIdentity() == null)
				throw new RedirectException(logIn);
			try {
				return new NewReplyPage(webInterface, getLoggedInOwnIdentity(), req);
			} catch (NoSuchBoardException e) {
				return new ErrorPage(webInterface, getLoggedInOwnIdentity(), req, "Unknown board "+req.getParam("name"), "Unknown board "+req.getParam("name"));
			} catch (NoSuchMessageException e) {
				return new ErrorPage(webInterface, getLoggedInOwnIdentity(), req, "Unknown message "+req.getParam("id"), "Unknown message "+req.getParam("id"));
			}
		}
		
		@Override
		public Toadlet showAsToadlet() {
			return messagesToadlet;
		}
		
		@Override
		public boolean isEnabled(ToadletContext ctx) {
			return super.isEnabled(ctx) && webInterface.getLoggedInOwnIdentity() != null;
		}
		
	}
	
	class NewBoardWebInterfaceToadlet extends WebInterfaceToadlet {

		protected NewBoardWebInterfaceToadlet(HighLevelSimpleClient client, WebInterface wi, NodeClientCore core, String pageTitle) {
			super(client, wi, core, pageTitle);
		}

		@Override
		WebPage makeWebPage(HTTPRequest req, ToadletContext context) throws RedirectException {
			if(!mFreetalk.wotConnected())
				return new WoTIsMissingPage(webInterface, req, mFreetalk.wotOutdated());
			return new NewBoardPage(webInterface, getLoggedInOwnIdentity(), req);
		}
		
		@Override
		public Toadlet showAsToadlet() {
			return messagesToadlet;
		}
		
		@Override
		public boolean isEnabled(ToadletContext ctx) {
			return super.isEnabled(ctx) && webInterface.getLoggedInOwnIdentity() != null;
		}
		
	}
	
	class GetPuzzleWebInterfaceToadlet extends WebInterfaceToadlet {

		protected GetPuzzleWebInterfaceToadlet(HighLevelSimpleClient client, WebInterface wi, NodeClientCore core, String pageTitle) {
			super(client, wi, core, pageTitle);
		}

		public void handleMethodGET(URI uri, HTTPRequest req, ToadletContext ctx) throws ToadletContextClosedException, IOException {
			WoTIdentityManager identityManager = (WoTIdentityManager)mFreetalk.getIdentityManager();
			
			Bucket dataBucket = null;
			OutputStream outputStream = null;
			
			try {
				IntroductionPuzzle puzzle = identityManager.getIntroductionPuzzle(req.getParam("PuzzleID"));
				
				// TODO: Store the list of allowed mime types in a constant. Also consider that we might have introduction puzzles with "Type=Audio" in the future.
				if(!puzzle.MimeType.equalsIgnoreCase("image/jpeg") &&
				  	!puzzle.MimeType.equalsIgnoreCase("image/gif") && 
				  	!puzzle.MimeType.equalsIgnoreCase("image/png")) {
					
					throw new Exception("Mime type '" + puzzle.MimeType + "' not allowed for introduction puzzles.");
				}
				
				dataBucket = core.tempBucketFactory.makeBucket(puzzle.Data.length);
				outputStream = dataBucket.getOutputStream();
				outputStream.write(puzzle.Data);
				
				FilterOutput output = null;
				try {
					output = ContentFilter.filter(dataBucket, core.tempBucketFactory, puzzle.MimeType, null, null);
				
					writeReply(ctx, 200, output.type, "OK", output.data);
				}
				finally {
					Closer.close(output.data);
				}
			}
			catch(Exception e) {
				sendErrorPage(ctx, 404, "Introduction puzzle not available", e.getMessage());
			}
			finally {
				Closer.close(outputStream);
				Closer.close(dataBucket);
			}
		}
		
		WebPage makeWebPage(HTTPRequest req, ToadletContext context) throws RedirectException {
			// not expected to make it here
			return new Welcome(webInterface, getLoggedInOwnIdentity(), req);
		}
	}

	public WebInterface(Freetalk myFreetalk) {
		try {
			logIn = new URI(Freetalk.PLUGIN_URI+"/LogIn");
		} catch (URISyntaxException e) {
			throw new Error(e);
		}
		
		mFreetalk = myFreetalk;
		mPageMaker = mFreetalk.getPluginRespirator().getPageMaker();
		mOwnIdentity = null;
		ToadletContainer container = mFreetalk.getPluginRespirator().getToadletContainer();
		
		mPageMaker.addNavigationCategory(Freetalk.PLUGIN_URI+"/", "Discussion", "Message boards", mFreetalk);
		
		NodeClientCore clientCore = mFreetalk.getPluginRespirator().getNode().clientCore;
		
		// Visible pages
		logInToadlet = new LogInWebInterfaceToadlet(null, this, clientCore, "LogIn");
		homeToadlet = new HomeWebInterfaceToadlet(null, this, clientCore, "");
		messagesToadlet = new MessagesWebInterfaceToadlet(null, this, clientCore, "messages");
		identitiesToadlet = new IdentitiesWebInterfaceToadlet(null, this, clientCore, "identities");
		logOutToadlet = new LogOutWebInterfaceToadlet(null, this, clientCore, "LogOut");
		
		container.register(homeToadlet, "Discussion", Freetalk.PLUGIN_URI+"/", true, "Log in", "Log in", false, logInToadlet);
		container.register(homeToadlet, "Discussion", Freetalk.PLUGIN_URI+"/", true, "Home", "Home page", false, homeToadlet);
		container.register(messagesToadlet, "Discussion", Freetalk.PLUGIN_URI+"/messages", true, "Boards", "View all boards", false, messagesToadlet);
		container.register(identitiesToadlet, "Discussion", Freetalk.PLUGIN_URI+"/identities", true, "Identities", "Manage your own and known identities", false, identitiesToadlet);
		container.register(logOutToadlet, "Discussion", Freetalk.PLUGIN_URI+"/LogOut", true, "Log out", "Log out", false, logOutToadlet);
		
		// Invisible pages
		createIdentityToadlet = new CreateIdentityWebInterfaceToadlet(null, this, clientCore, "CreateIdentity");
		newThreadToadlet = new NewThreadWebInterfaceToadlet(null, this, clientCore, "NewThread");
		showBoardToadlet = new ShowBoardWebInterfaceToadlet(null, this, clientCore, "showBoard");
		showThreadToadlet = new ShowThreadWebInterfaceToadlet(null, this, clientCore, "showThread");
		newReplyToadlet = new NewReplyWebInterfaceToadlet(null, this, clientCore, "NewReply");
		newBoardToadlet = new NewBoardWebInterfaceToadlet(null, this, clientCore, "NewBoard");
		changeTrustToadlet = new ChangeTrustWebInterfaceToadlet(null, this, clientCore, "ChangeTrust");
		getPuzzleToadlet = new GetPuzzleWebInterfaceToadlet(null, this, clientCore, "GetPuzzle");
		
		container.register(logInToadlet, null, Freetalk.PLUGIN_URI + "/LogIn", true, false);
		container.register(createIdentityToadlet, null, Freetalk.PLUGIN_URI + "/CreateIdentity", true, false);
		container.register(newThreadToadlet, null, Freetalk.PLUGIN_URI + "/NewThread", true, false);
		container.register(showBoardToadlet, null, Freetalk.PLUGIN_URI + "/showBoard", true, false);
		container.register(showThreadToadlet, null, Freetalk.PLUGIN_URI + "/showThread", true, false);
		container.register(newReplyToadlet, null, Freetalk.PLUGIN_URI + "/NewReply", true, false);
		container.register(newBoardToadlet, null, Freetalk.PLUGIN_URI + "/NewBoard", true, false);
		container.register(changeTrustToadlet, null, Freetalk.PLUGIN_URI + "/ChangeTrust", true, false);
		container.register(getPuzzleToadlet, null, Freetalk.PLUGIN_URI + "/GetPuzzle", true, false);
	}

	private void setLoggedInOwnIdentity(FTOwnIdentity user) {
		mOwnIdentity = user;
	}
	
	private FTOwnIdentity getLoggedInOwnIdentity() {
		return mOwnIdentity;
	}

	public final Freetalk getFreetalk() {
		return mFreetalk;
	}

	public final PageMaker getPageMaker() {
		return mPageMaker;
	}
	
	public void terminate() {
		ToadletContainer container = mFreetalk.getPluginRespirator().getToadletContainer();
		for(Toadlet t : new Toadlet[] { 
				homeToadlet,
				messagesToadlet,
				identitiesToadlet,
				logOutToadlet,
				logInToadlet,
				createIdentityToadlet,
				newThreadToadlet,
				showBoardToadlet,
				showThreadToadlet,
				newReplyToadlet,
				newBoardToadlet,
				getPuzzleToadlet
		}) container.unregister(t);
		mPageMaker.removeNavigationCategory("Discussion");
	}

}
