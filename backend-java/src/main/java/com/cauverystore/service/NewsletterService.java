package com.cauverystore.service;

import com.cauverystore.entities.NewsletterSubscription;
import com.cauverystore.repository.NewsletterSubscriptionRepository;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class NewsletterService {
    private final NewsletterSubscriptionRepository repo;
    public NewsletterService(NewsletterSubscriptionRepository repo) { this.repo = repo; }

    public List<NewsletterSubscription> getAll() { return repo.findAll(); }
    public long getCount() { return repo.count(); }
    public void unsubscribe(Long id) { repo.deleteById(id); }
}
