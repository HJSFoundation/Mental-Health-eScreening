package gov.va.escreening.repository;

import gov.va.escreening.entity.Event;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.persistence.NoResultException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

@Repository
public class EventRepositoryImpl extends AbstractHibernateRepository<Event> 
    implements EventRepository {

    private static final Logger logger = LoggerFactory.getLogger(EventRepositoryImpl.class);

    public EventRepositoryImpl() {
        super();

        setClazz(Event.class);
    }
    
    @Override
    public List<Event> getEventByTypeFilteredByObjectIds(int eventTypeId, Collection<Integer> objectIds){
        
        //logger.trace("Getting events with type {} and filtered by {} related object IDs", eventTypeId, objectIds.size());
        
        if(objectIds.isEmpty())
            return Collections.emptyList();
        
        String sql = "SELECT e FROM Event e INNER JOIN e.rules WHERE e.eventType.eventTypeId = :eventTypeId and e.relatedObjectId IN (:objectIds) GROUP BY e.eventId, e.name, e.relatedObjectId, e.dateCreated, e.eventType";
        
        return entityManager
            .createQuery(sql, Event.class)
            .setParameter("eventTypeId", eventTypeId)
            .setParameter("objectIds", objectIds)
            .getResultList();
    }
    
    @Override
    public List<Event> getEventByType(int eventTypeId){        
        String sql = "SELECT e FROM Event e WHERE e.eventType.eventTypeId = :eventTypeId";
        
        return entityManager
            .createQuery(sql, Event.class)
            .setParameter("eventTypeId", eventTypeId)
            .getResultList();
    }
    
    
    @Override
    public Event getEventForObject(int relatedObjectId, int eventTypeId){
        //logger.trace("Getting event with type ID of {} and related object ID of {}", eventTypeId, relatedObjectId);
        String sql = "SELECT e FROM Event e WHERE e.eventType.eventTypeId = :eventTypeId AND e.relatedObjectId = :relatedObjectId";
        
        try{
            return entityManager
            .createQuery(sql, Event.class)
            .setParameter("eventTypeId", eventTypeId)
            .setParameter("relatedObjectId", relatedObjectId)
            .getSingleResult();
        }
        catch(NoResultException e){
            return null;
        }
    }
}
