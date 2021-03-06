/* 
 * Copyright (C) 2015 Jeremy Wildsmith.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package io.github.jevaengine.rpg.entity.character;

import io.github.jevaengine.math.Vector3F;
import io.github.jevaengine.rpg.AttributeSet;
import io.github.jevaengine.rpg.IImmutableAttributeSet;
import io.github.jevaengine.rpg.dialogue.IDialogueRoute;
import io.github.jevaengine.rpg.dialogue.IDialogueRouteFactory;
import io.github.jevaengine.rpg.entity.character.tasks.*;
import io.github.jevaengine.rpg.entity.character.tasks.SearchForTask.ISearchListener;
import io.github.jevaengine.rpg.item.IItemStore;
import io.github.jevaengine.script.IFunctionFactory;
import io.github.jevaengine.script.IScriptBuilder;
import io.github.jevaengine.script.IScriptBuilder.ScriptConstructionException;
import io.github.jevaengine.script.ScriptEvent;
import io.github.jevaengine.script.ScriptExecuteException;
import io.github.jevaengine.util.IObserverRegistry;
import io.github.jevaengine.util.Observers;
import io.github.jevaengine.world.Direction;
import io.github.jevaengine.world.World;
import io.github.jevaengine.world.entity.DefaultEntityTaskModel;
import io.github.jevaengine.world.entity.IEntity;
import io.github.jevaengine.world.entity.IEntityTaskModel;
import io.github.jevaengine.world.entity.WorldAssociationException;
import io.github.jevaengine.world.entity.tasks.ITask;
import io.github.jevaengine.world.pathfinding.AStarRouteFactory;
import io.github.jevaengine.world.pathfinding.EntityRoutingRules;
import io.github.jevaengine.world.physics.IPhysicsBody;
import io.github.jevaengine.world.physics.NullPhysicsBody;
import io.github.jevaengine.world.physics.PhysicsBodyDescription;
import io.github.jevaengine.world.scene.model.IActionSceneModel;
import io.github.jevaengine.world.scene.model.IImmutableSceneModel;
import io.github.jevaengine.world.scene.model.ISceneModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class DefaultRpgCharacter implements IRpgCharacter
{	
	private final Logger m_logger = LoggerFactory.getLogger(DefaultRpgCharacter.class);

	private final PhysicsBodyDescription m_physicsBodyDescription;	
	
	private final String m_name;

	private final AttributeSet m_attributes;

	private final IDialogueResolver m_dialogueResolver;
	private final IMovementResolver m_movementResolver;
	private final IVisionResolver m_visionResolver;
	private final IAllegianceResolver m_allegianceResolver;
	private final IStatusResolver m_statusResolver;

	
	private final IRpgCharacterMechanicResolver m_resolvers[];
	
	private final IItemStore m_inventory;
	private final ILoadout m_loadout;
	
	private final IEntityTaskModel m_taskModel;
	
	private final Observers m_observers = new Observers();
	private final RpgCharacterBridge m_bridge;

	private final ISceneModel m_model;
	private final IDialogueRouteFactory m_dialogueRouteFactory;
	
	private World m_world = null;
	private IPhysicsBody m_body = new NullPhysicsBody();
	
	private final Map<String, Integer> m_flags = new HashMap<>();
	
	public DefaultRpgCharacter(IScriptBuilder scriptBuilder,
						IDialogueRouteFactory dialogueRotueFactory,
						AttributeSet attributes,
						IStatusResolverFactory statusResolver,
						IDialogueResolverFactory dialogueResolver,
						IMovementResolverFactory movementResolver,
						IVisionResolverFactory visionResolver,
						IAllegianceResolverFactory allegianceResolver,
						ILoadout loadout,
						IItemStore inventory,
						IActionSceneModel model,
						PhysicsBodyDescription physicsBodyDescription,
						String name)

	{
		m_dialogueRouteFactory = dialogueRotueFactory;
		
		m_name = name;
		m_physicsBodyDescription = physicsBodyDescription;

		m_inventory = inventory;
		m_loadout = loadout;

		m_taskModel = new DefaultEntityTaskModel(this);
	
		m_attributes = attributes;
		
		m_dialogueResolver = dialogueResolver.create(this, m_attributes, model);
		m_statusResolver = statusResolver.create(this, m_attributes, model);
		m_movementResolver = movementResolver.create(this, m_attributes, model);
		m_visionResolver = visionResolver.create(this, m_attributes, model);
		m_allegianceResolver = allegianceResolver.create(this, attributes, model);

		m_resolvers = new IRpgCharacterMechanicResolver[] {
			m_dialogueResolver,
			m_statusResolver,
			m_movementResolver,
			m_visionResolver,
			m_allegianceResolver
		};

		
		IActionSceneModel initialModel = model;
		for(IRpgCharacterMechanicResolver r : m_resolvers)
			initialModel = r.decorate(initialModel);
		
		m_model = initialModel;
		
		m_bridge = new RpgCharacterBridge(scriptBuilder.getFunctionFactory(), scriptBuilder.getUri());
		
		try
		{
			scriptBuilder.create(m_bridge);
		} catch (ScriptConstructionException e)
		{
			m_logger.error("Failed instantiate behavior for " + getInstanceName() + ". Assuming null behavior.", e);	
		}
	
	}

	@Override
	public void dispose()
	{
		if(m_world != null)
			m_world.removeEntity(this);
		
		m_model.dispose();
		m_observers.clear();
	}
	
	private void createPhysicsBody()
	{
		if(m_world == null)
			return;
		
		m_body = m_world.getPhysicsWorld().createBody(this, m_physicsBodyDescription);		

		m_observers.raise(IEntityBodyObserver.class).bodyChanged(new NullPhysicsBody(), m_body);
	}
	
	private void destoryPhysicsBody()
	{
		m_body.destory();
		m_body = new NullPhysicsBody();
		
		m_observers.raise(IEntityBodyObserver.class).bodyChanged(new NullPhysicsBody(), new NullPhysicsBody());
	}

	@Override
	public IImmutableAttributeSet getAttributes() {
		return m_attributes;
	}

	@Override
	public IItemStore getInventory()
	{
		return m_inventory;
	}

	@Override
	public ILoadout getLoadout()
	{
		return m_loadout;
	}

	@Override
	public IStatusResolver getStatusResolver()
	{
		return m_statusResolver;
	}

	@Override
	public IDialogueResolver getDialogueResolver()
	{
		return m_dialogueResolver;
	}

	@Override
	public IMovementResolver getMovementResolver()
	{
		return m_movementResolver;
	}
	
	@Override
	public IVisionResolver getVisionResolver()
	{
		return m_visionResolver;
	}
	
	@Override
	public IAllegianceResolver getAllegianceResolver()
	{
		return m_allegianceResolver;
	}
	
	@Override
	public World getWorld()
	{
		return m_world;
	}

	@Override
	public void associate(World world)
	{
		if(m_world != null)
			throw new WorldAssociationException("Entity already associated to world.");
	
		m_world = world;
		
		createPhysicsBody();
		
		m_observers.raise(IEntityWorldObserver.class).enterWorld();
	}

	@Override
	public void disassociate()
	{
		if(m_world == null)
			throw new WorldAssociationException("Entity not associated to world.");
		
		m_observers.raise(IEntityWorldObserver.class).leaveWorld();
		
		m_world = null;
		
		destoryPhysicsBody();
	}

	@Override
	public String getInstanceName()
	{
		return m_name;
	}

	@Override
	public Map<String, Integer> getFlags()
	{
		return Collections.unmodifiableMap(m_flags);
	}

	@Override
	public boolean isStatic()
	{
		return false;
	}

	@Override
	public IImmutableSceneModel getModel()
	{
		return m_model;
	}

	@Override
	public IPhysicsBody getBody()
	{
		return m_body;
	}

	@Override
	public IEntityTaskModel getTaskModel()
	{
		return m_taskModel;
	}

	@Override
	public IObserverRegistry getObservers()
	{
		return m_observers;
	}

	@Override
	public EntityBridge getBridge()
	{
		return m_bridge;
	}

	@Override
	public void update(int delta)
	{
		m_taskModel.update(delta);
		
		for(IRpgCharacterMechanicResolver r : m_resolvers)
			r.update(delta);
		
		m_model.update(delta);
	}
	
	public class RpgCharacterBridge extends EntityBridge
	{
		private final ITask m_lookTask;
		public final ScriptEvent onLookFound;
		public final ScriptEvent onDie;
		public final ScriptEvent onRevive;
		
		public RpgCharacterBridge(IFunctionFactory functionFactory, URI scriptUri)
		{
			super(DefaultRpgCharacter.this, functionFactory);
			
			onLookFound = new ScriptEvent(functionFactory);
			onDie = new ScriptEvent(functionFactory);
			onRevive = new ScriptEvent(functionFactory);
			
			DefaultRpgCharacter.this.m_statusResolver.getObservers().add(new IStatusResolver.IStatusResolverObserver() {
				@Override
				public void died()
				{
					try
					{
						onDie.fire();
					} catch (ScriptExecuteException e)
					{
						m_logger.error("Error occured executing onDie script event.");
					}
				}

				@Override
				public void revived()
				{
					try
					{
						onRevive.fire();
					} catch (ScriptExecuteException e)
					{
						m_logger.error("Error occured executing onRevie script event.");
					}
				}
			});
			
			m_lookTask = new SearchForTask<>(IRpgCharacter.class, new ISearchListener<IRpgCharacter>() {
				@Override
				public void found(IRpgCharacter entity)
				{
					try {
						onLookFound.fire(entity.getBridge());
					} catch (ScriptExecuteException e)
					{
						m_logger.error("Error occured executing onLookFound script event", e);
					}
				}
			});
		}
		
		public void setFlag(String name, int value)
		{
			m_flags.put(name, value);
		}
		
		public boolean isDead()
		{
			return m_statusResolver.isDead();
		}
		
		public void wonder(int radius)
		{
			getTaskModel().addTask(new WonderTask(
								new AStarRouteFactory(),
								new EntityRoutingRules(DefaultRpgCharacter.this, Direction.ALL_DIRECTIONS), radius));
		}
		
		public void speakTo(EntityBridge target)
		{
			IEntity targetEntity = target.getEntity();
			if(!(targetEntity instanceof IRpgCharacter))
				m_logger.error("Attack Range test target must be an rpg character.");
			else
				getTaskModel().addTask(new SpeakToTask((IRpgCharacter)targetEntity));
		}
		
		public void listenTo(EntityBridge target)
		{
			IEntity targetEntity = target.getEntity();
			if(!(targetEntity instanceof IRpgCharacter))
				m_logger.error("Attack Range test target must be an rpg character.");
			else
				getTaskModel().addTask(new ListenToTask((IRpgCharacter)targetEntity));
		}
		
		public void narrate(String dialogue)
		{
			try
			{
				IDialogueRoute route = m_dialogueRouteFactory.create(new URI(dialogue));
				getTaskModel().addTask(new NarrateTask(route));
			} catch (URISyntaxException | IDialogueRouteFactory.DialogueRouteConstructionException e)
			{
				m_logger.error("Unable to narrate dialogue", e);
			}
		}
		
		public void moveTo(Vector3F location, float arrivalTolorance, float waypointTolorance)
		{
			getTaskModel().addTask(new MovementTask(new AStarRouteFactory(), new EntityRoutingRules(DefaultRpgCharacter.this, Direction.ALL_DIRECTIONS), location.getXy(), arrivalTolorance, Integer.MAX_VALUE));
		}
		
		public void moveTo(EntityBridge bridge)
		{
			IEntity owner = bridge.getEntity();
			
			getTaskModel().addTask(new FollowEntityTask(new AStarRouteFactory(), new EntityRoutingRules(DefaultRpgCharacter.this, Direction.ALL_DIRECTIONS), owner));
		}
		
		public boolean isConflictingAllegiance(EntityBridge otherBridge)
		{
			IEntity other = otherBridge.getEntity();

			if(!(other instanceof IRpgCharacter))
				return false;
			
			return ((DefaultRpgCharacter)getEntity()).m_allegianceResolver.isConflictingAllegiance((IRpgCharacter)other);
		}
		
		public void beginLook()
		{
			if(!getTaskModel().isTaskActive(m_lookTask))
				getTaskModel().addTask(m_lookTask);
		}
		
		public void endLook()
		{
			if(getTaskModel().isTaskActive(m_lookTask))
				getTaskModel().cancelTask(m_lookTask);
		}
	}
}